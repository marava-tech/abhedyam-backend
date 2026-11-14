package com.abhedyam.service;

import com.abhedyam.dto.BillingStatusResponse;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.service.interfaces.IBillingService;
import com.abhedyam.service.interfaces.IReferralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Access model: one date, {@code owner.accessUntil}. Signup gives a free trial; a Play payment moves it to the
 * end of the paid period; referral rewards add days. After it the app is read-only (records stay viewable and
 * exportable). Prices live in Google Play / RevenueCat, not here. Spec: specs/billing.md
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService implements IBillingService {

    public static final int TRIAL_DAYS = 30;
    public static final int FOUNDING_SEATS = 100;
    /** The earned second free month: once, during the trial, when the shop has this many customers and payments. */
    public static final int BONUS_DAYS = 30, BONUS_CUSTOMERS = 10, BONUS_PAYMENTS = 5;

    /** RevenueCat events that carry a new access end date. */
    private static final Set<String> ACCESS_EVENTS = Set.of(
            "INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION",
            "NON_RENEWING_PURCHASE", "SUBSCRIPTION_EXTENDED");
    /** Of those, the ones that mean money was taken (unless period_type is TRIAL). */
    private static final Set<String> PAID_EVENTS = Set.of(
            "INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION", "NON_RENEWING_PURCHASE");

    private final OwnerRepository ownerRepository;
    private final IReferralService referralService;
    private final NotificationDispatcher notifications;
    private final ShopMemberService memberService;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public BillingStatusResponse status(UUID ownerId) {
        Owner owner = find(ownerId);
        Instant now = Instant.now();
        Instant until = owner.getAccessUntil();
        boolean active = until != null && until.isAfter(now);
        long daysLeft = active ? (long) Math.ceil(Duration.between(now, until).toHours() / 24.0) : 0;
        long foundingLeft = Math.max(0, FOUNDING_SEATS - ownerRepository.countByFirstPaidAtIsNotNull());
        Plan plan = Plan.of(owner);
        return new BillingStatusResponse(until, active, owner.getPlayProductId() != null,
                owner.getPlayProductId(), daysLeft, foundingLeft, plan.name(), plan.phones, plan.helpers,
                owner.getTrialBonusAt() != null);
    }

    @Override
    @Transactional
    public boolean applyRevenueCatEvent(Map<String, Object> event) {
        UUID ownerId = parseOwnerId(event.get("app_user_id"));
        if (ownerId == null) return false;
        Owner owner = ownerRepository.findById(ownerId).orElse(null);
        if (owner == null) return false;

        String type = String.valueOf(event.get("type"));
        if ("EXPIRATION".equals(type)) {
            owner.setPlayProductId(null);
            ownerRepository.save(owner);
            memberService.enforcePlan(ownerId);
            log.info("Play subscription ended: owner={}", ownerId);
            return true;
        }
        if (!ACCESS_EVENTS.contains(type) || !(event.get("expiration_at_ms") instanceof Number ms)) return false;

        Instant expiry = Instant.ofEpochMilli(ms.longValue());
        // max(): RevenueCat retries and may deliver out of order; never shorten access.
        if (owner.getAccessUntil() == null || expiry.isAfter(owner.getAccessUntil())) owner.setAccessUntil(expiry);
        if (event.get("product_id") instanceof String product) owner.setPlayProductId(product);

        boolean paid = PAID_EVENTS.contains(type) && !"TRIAL".equals(event.get("period_type"));
        boolean firstPayment = paid && owner.getFirstPaidAt() == null;
        if (firstPayment) owner.setFirstPaidAt(Instant.now());
        ownerRepository.save(owner);
        memberService.enforcePlan(ownerId); // Business → Shop pauses extra helpers/phones; back to Business unpauses
        log.info("Play {}: owner={}, product={}, accessUntil={}", type, ownerId, owner.getPlayProductId(), owner.getAccessUntil());

        if (firstPayment) referralService.onFirstPayment(owner);
        return true;
    }

    @Override
    @Transactional
    public void grantDays(UUID ownerId, int days) {
        Owner owner = find(ownerId);
        addDays(owner, days);
        ownerRepository.save(owner);
    }

    @Override
    @Transactional
    public void endAccess(UUID ownerId) {
        Owner owner = find(ownerId);
        owner.setAccessUntil(Instant.now());
        ownerRepository.save(owner);
    }

    /** Extends from the later of now and the current end, so days are never lost or doubled up. */
    static void addDays(Owner owner, int days) {
        Instant now = Instant.now();
        Instant from = owner.getAccessUntil() != null && owner.getAccessUntil().isAfter(now) ? owner.getAccessUntil() : now;
        owner.setAccessUntil(from.plus(days, ChronoUnit.DAYS));
    }

    /** 10:30 IST: "3 days left", "last day", "trial ended" for owners who are not paying. Dedupe key keeps it to one push each. */
    @Scheduled(cron = "0 30 10 * * *", zone = "Asia/Kolkata")
    public void remindAccessEnding() {
        Instant now = Instant.now();
        for (Owner owner : ownerRepository.findByPlayProductIdIsNullAndAccessUntilBetween(now.minus(1, ChronoUnit.DAYS), now.plus(73, ChronoUnit.HOURS))) {
            Instant until = owner.getAccessUntil();
            long days = until.isAfter(now) ? (long) Math.ceil(Duration.between(now, until).toHours() / 24.0) : 0;
            if (days != 3 && days != 1 && days != 0) continue;
            com.abhedyam.util.Txt title = com.abhedyam.util.Txt.of("notif.billing.days" + days + ".title");
            com.abhedyam.util.Txt body = com.abhedyam.util.Txt.of("notif.billing.days" + days + ".body");
            try {
                notifications.notify(owner.getId(), NotificationCategory.BILLING, NotificationType.INFO, title, body,
                        null, null, "access-end-" + days + "-" + until.getEpochSecond());
            } catch (Exception e) {
                log.warn("Access reminder failed: owner={}", owner.getId(), e);
            }
        }
    }

    /** Every 15 min: trial shops that reached 10 customers and 5 payments get the second free month (once) and a push. */
    // ponytail: two counts per trial shop per run; move to the customer/payment save path if trial shops reach thousands
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void grantEarnedMonth() {
        for (Owner owner : ownerRepository.findByFirstPaidAtIsNullAndPlayProductIdIsNullAndTrialBonusAtIsNullAndAccessUntilAfter(Instant.now())) {
            if (!earnedMonth(customerRepository.countByOwnerId(owner.getId()),
                    paymentRepository.countSuccessfulPaymentsByOwnerIdAndDateRange(owner.getId(), null, null))) continue;
            addDays(owner, BONUS_DAYS);
            owner.setTrialBonusAt(Instant.now());
            ownerRepository.save(owner);
            log.info("Earned free month: owner={}, accessUntil={}", owner.getId(), owner.getAccessUntil());
            try {
                notifications.notify(owner.getId(), NotificationCategory.BILLING, NotificationType.INFO,
                        com.abhedyam.util.Txt.of("notif.billing.bonus.title"), com.abhedyam.util.Txt.of("notif.billing.bonus.body"),
                        null, null, "trial-bonus");
            } catch (Exception e) {
                log.warn("Earned-month push failed: owner={}", owner.getId(), e);
            }
        }
    }

    static boolean earnedMonth(long customers, long payments) {
        return customers >= BONUS_CUSTOMERS && payments >= BONUS_PAYMENTS;
    }

    private Owner find(UUID ownerId) {
        return ownerRepository.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
    }

    private static UUID parseOwnerId(Object appUserId) {
        try {
            return appUserId instanceof String s ? UUID.fromString(s) : null;
        } catch (IllegalArgumentException e) {
            return null; // RevenueCat anonymous ids ($RCAnonymousID:...) are not owners
        }
    }
}
