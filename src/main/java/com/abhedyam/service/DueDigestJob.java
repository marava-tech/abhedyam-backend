package com.abhedyam.service;

import com.abhedyam.dto.OwnerDueResponse;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.repository.FcmDetailsRepository;
import com.abhedyam.repository.NotificationRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.service.interfaces.IPaymentDueService;
import com.abhedyam.util.PackageConstants;
import com.abhedyam.util.Txt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Once a day per owner, at their digest time: how much is due (with today's and overdue dues), and a
 * reminder the day before every unpaid due, plus the extra earlier one the owner chose for it. Only owners with the business app
 * installed (an FCM token) and the digest turned on. The dispatcher dedupes on the date, so a
 * restart or a late tick never sends twice, and moves it past quiet hours.
 * ponytail: every 15 min we recompute for owners with nothing due yet; fine for a few hundred owners,
 * cache a "nothing due today" marker if it gets slow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DueDigestJob {

    private final FcmDetailsRepository fcmDetails;
    private final INotificationSettingsService settings;
    private final NotificationRepository notifications;
    private final SaleItemRepository saleItems;
    private final PaymentRepository payments;
    private final NotificationDispatcher dispatcher;
    private final IPaymentDueService paymentDues;
    private final com.abhedyam.service.interfaces.IItemService items;
    private final com.abhedyam.service.interfaces.IOwnerSettingsService ownerSettings;

    @Scheduled(cron = "0 */15 * * * *")
    public void run() {
        run(Instant.now());
    }

    void run(Instant now) {
        for (UUID ownerId : fcmDetails.findUserIdsByPackageName(PackageConstants.BUSINESS_APP_PACKAGE)) {
            try {
                sendIfDue(ownerId, now);
            } catch (Exception e) {
                log.error("Due digest failed for owner {}: {}", ownerId, e.getMessage(), e);
            }
        }
    }

    private void sendIfDue(UUID ownerId, Instant now) {
        NotificationSettings s = settings.forOwner(ownerId);
        ZonedDateTime local = now.atZone(ZoneId.of(s.getTimezone()));
        if (local.toLocalTime().isBefore(s.getDigestTime())) return;
        LocalDate today = local.toLocalDate();
        List<OwnerDueResponse> dues = paymentDues.ownerDues(ownerId);
        remindAhead(ownerId, dues, today);
        if (ownerSettings.villageMode(ownerId)) remindVisits(ownerId, today); // no village pushes when villages view is off

        if (s.getMutedCategories().contains(NotificationCategory.DUE_DIGEST)) return;
        String key = "digest:" + today;
        if (notifications.findByOwnerIdAndDedupeKey(ownerId, key).isPresent()) return;

        DueDigest digest = DueDigest.of(toMap(saleItems.sumTotalsByCustomer(ownerId)), toMap(payments.sumSuccessfulByCustomer(ownerId)),
            oldestUnpaid(saleItems.oldestUnpaidSaleByCustomer(ownerId)), now);
        Txt stockLine = stockLine(ownerId);
        if (digest == null && stockLine == null) return;
        List<Txt> parts = new java.util.ArrayList<>();
        Txt duesLine = DueDigest.duesLine(dues, today);
        if (duesLine != null) parts.add(duesLine);
        if (digest != null) parts.add(digest.text());
        if (stockLine != null) parts.add(stockLine);
        Txt title = Txt.of(digest != null ? "notif.digest.title.money" : "notif.digest.title.stock");
        dispatcher.notify(ownerId, NotificationCategory.DUE_DIGEST, NotificationType.INFO, title, Txt.of("notif.join", parts), "DIGEST", null, key);
    }

    /** A stock problem must never stop the money digest. */
    private Txt stockLine(UUID ownerId) {
        try {
            return DueDigest.stockLine(items.all(ownerId));
        } catch (Exception e) {
            log.warn("Digest stock line skipped for owner {}: {}", ownerId, e.getMessage());
            return null;
        }
    }

    /** Key holds the date and the lead time, so changing either re-arms the reminder; the dispatcher drops repeats. */
    private void remindAhead(UUID ownerId, List<OwnerDueResponse> dues, LocalDate today) {
        for (OwnerDueResponse o : dues) {
            var d = o.getDue();
            if (d.getStatus().equals("PAID")) continue;
            int n;
            if (d.getDueDate().minusDays(1).equals(today)) n = 1;
            else if (d.getEarlyReminderDays() != null && d.getDueDate().minusDays(d.getEarlyReminderDays()).equals(today)) n = d.getEarlyReminderDays();
            else continue;
            Object name = o.getCustomerName() == null ? Txt.of("notif.customer") : o.getCustomerName();
            Txt title = n == 1 ? Txt.of("notif.due.title.tomorrow", name) : Txt.of("notif.due.title.days", name, n);
            List<Object> body = new java.util.ArrayList<>(List.of(Txt.of("notif.due.body", PaymentDueService.rupees(d.getRemaining()), d.getDueDate())));
            if (d.getNote() != null) body.add(d.getNote()); // the owner's own words, as typed
            dispatcher.notify(ownerId, NotificationCategory.OWNER_REMINDER, NotificationType.INFO, title, Txt.of("notif.join", body),
                "DUE", d.getId(), "due-remind:" + d.getId() + ":" + d.getDueDate() + ":" + n);
        }
    }

    /** Day before a planned village visit: "Visit Hanamkonda tomorrow" · "6 customers owe ₹18,400". */
    private void remindVisits(UUID ownerId, LocalDate today) {
        for (var v : paymentDues.ownerVisits(ownerId)) {
            if (!v.getVisitDate().equals(today.plusDays(1))) continue;
            List<Object> body = new java.util.ArrayList<>(List.of(v.getOwingCount() == 0 ? Txt.of("notif.visit.nobody")
                : Txt.of("notif.visit.owing", v.getOwingCount(), PaymentDueService.rupees(v.getOwingAmount()))));
            if (v.getNote() != null) body.add(v.getNote());
            dispatcher.notify(ownerId, NotificationCategory.OWNER_REMINDER, NotificationType.INFO, Txt.of("notif.visit.title", v.getVillage()),
                Txt.of("notif.join", body),
                "VISIT", v.getId(), "visit-remind:" + v.getId() + ":" + v.getVisitDate());
        }
    }

    private static Map<UUID, BigDecimal> toMap(java.util.List<Object[]> rows) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put(UUID.fromString(r[0].toString()), new BigDecimal(r[1].toString())); // same parsing as LocationDetailsService: row types vary by driver
        return m;
    }

    private static Map<UUID, java.time.Instant> oldestUnpaid(java.util.List<Object[]> rows) {
        Map<UUID, java.time.Instant> m = new HashMap<>();
        for (Object[] r : rows) {
            if (r[1] == null) continue;
            m.put(UUID.fromString(r[0].toString()),
                r[1] instanceof java.time.Instant i ? i : ((java.sql.Timestamp) r[1]).toInstant());
        }
        return m;
    }
}
