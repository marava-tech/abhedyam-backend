package com.abhedyam.service;

import com.abhedyam.dto.ReferralSummaryResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Partner;
import com.abhedyam.model.PartnerEarning;
import com.abhedyam.model.Referral;
import com.abhedyam.model.enums.ReferralStatus;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PartnerEarningRepository;
import com.abhedyam.repository.PartnerRepository;
import com.abhedyam.repository.ReferralRepository;
import com.abhedyam.service.interfaces.IReferralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Give 15 days, get a month: the friend gets {@link #FRIEND_BONUS_DAYS} extra days when they use a code; the
 * referrer is rewarded only when the friend makes a first Play payment. A payment is the proof the shop is real,
 * so there are no activity or device checks. A partner's code (agent / accountant) works the same for the shop;
 * the partner earns {@link #PARTNER_RUPEES} when the shop first pays. Spec: specs/billing.md
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService implements IReferralService {

    public static final int FRIEND_BONUS_DAYS = 15;
    public static final int REFERRER_REWARD_DAYS = 30;
    public static final int REFERRER_REWARD_RUPEES = 200;
    public static final int PARTNER_RUPEES = 250;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I, O, 0, 1
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OwnerRepository ownerRepository;
    private final ReferralRepository referralRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerEarningRepository partnerEarningRepository;

    @Override
    @Transactional
    public ReferralSummaryResponse summary(UUID ownerId) {
        Owner owner = find(ownerId);
        String code = codeFor(owner);
        List<Referral> referrals = referralRepository.findByReferrerOwnerIdOrderByCreatedAtDesc(ownerId);
        List<ReferralSummaryResponse.Friend> friends = referrals.stream()
                .map(r -> new ReferralSummaryResponse.Friend(
                        ownerRepository.findById(r.getRefereeOwnerId()).map(Owner::getBusinessName).orElse("A shop"),
                        r.getCreatedAt(), r.getStatus() == ReferralStatus.PAID))
                .toList();
        long months = referrals.stream().filter(r -> r.getStatus() == ReferralStatus.PAID).count();
        return new ReferralSummaryResponse(code, "https://duesetu.com/join?ref=" + code, months,
                owner.getReferredBy() != null || owner.getPartnerId() != null, friends);
    }

    @Override
    @Transactional
    public void apply(UUID refereeOwnerId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        Owner referee = find(refereeOwnerId);
        if (referee.getReferredBy() != null || referee.getPartnerId() != null) {
            throw new BusinessException("ALREADY_REFERRED", "You have already used a referral code");
        }
        if (referee.getFirstPaidAt() != null) {
            throw new BusinessException("ALREADY_PAID", "Referral codes are for new shops that have not paid yet");
        }
        Owner referrer = ownerRepository.findByReferralCode(code).orElse(null);
        if (referrer == null) {
            Partner partner = partnerRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException("CODE_NOT_FOUND", "This code is not right. Check it and try again"));
            referee.setPartnerId(partner.getId().toString());
            BillingService.addDays(referee, FRIEND_BONUS_DAYS);
            ownerRepository.save(referee);
            log.info("Partner code used: owner={}, partner={}", refereeOwnerId, partner.getId());
            return;
        }
        if (referrer.getId().equals(refereeOwnerId)) {
            throw new BusinessException("SELF_REFERRAL", "You can't use your own code");
        }

        // Only an older account can refer a newer one. Otherwise two shops could use each other's codes and both
        // collect the bonus days. A missing timestamp is denied too (it never happens for a saved owner).
        Instant referrerSince = referrer.getCreatedAt();
        Instant refereeSince = referee.getCreatedAt();
        if (referrerSince == null || refereeSince == null || !refereeSince.isAfter(referrerSince)) {
            throw new BusinessException("REFERRER_NOT_OLDER",
                    "This code belongs to a shop that joined after you. Codes only work for shops that joined after the code owner");
        }

        referee.setReferredBy(referrer.getId().toString());
        BillingService.addDays(referee, FRIEND_BONUS_DAYS);
        ownerRepository.save(referee);

        Referral referral = new Referral();
        referral.setReferrerOwnerId(referrer.getId());
        referral.setRefereeOwnerId(refereeOwnerId);
        referralRepository.save(referral);
        log.info("Referral joined: referee={}, referrer={}", refereeOwnerId, referrer.getId());
    }

    @Override
    @Transactional
    public void onFirstPayment(Owner payer) {
        if (payer.getPartnerId() != null && !partnerEarningRepository.existsByOwnerId(payer.getId())) {
            PartnerEarning earning = new PartnerEarning();
            earning.setPartnerId(UUID.fromString(payer.getPartnerId()));
            earning.setOwnerId(payer.getId());
            earning.setRupees(PARTNER_RUPEES);
            partnerEarningRepository.save(earning);
            log.info("Partner earned ₹{}: partner={}, shop={}", PARTNER_RUPEES, payer.getPartnerId(), payer.getId());
        }
        Referral referral = referralRepository.findByRefereeOwnerId(payer.getId()).orElse(null);
        if (referral == null || referral.getStatus() == ReferralStatus.PAID) return;
        Owner referrer = ownerRepository.findById(referral.getReferrerOwnerId()).orElse(null);
        if (referrer == null) return;

        // Referrers are always rewarded in days, never money (also when they already pay on Play).
        BillingService.addDays(referrer, REFERRER_REWARD_DAYS);
        ownerRepository.save(referrer);
        referral.setStatus(ReferralStatus.PAID);
        referral.setRewardCash(false);
        referral.setRewardedAt(Instant.now());
        referralRepository.save(referral);
        log.info("Referral rewarded: referrer={}, friend={}, reward={} days", referrer.getId(), payer.getId(), REFERRER_REWARD_DAYS);
    }

    private String codeFor(Owner owner) {
        if (owner.getReferralCode() != null) return owner.getReferralCode();
        String code = newCode(c -> ownerRepository.existsByReferralCode(c) || partnerRepository.existsByCode(c));
        owner.setReferralCode(code);
        ownerRepository.save(owner);
        return code;
    }

    /** ABH + 5 unambiguous characters, unique across owner and partner codes. */
    public static String newCode(Predicate<String> taken) {
        String code;
        do {
            StringBuilder sb = new StringBuilder("ABH");
            for (int i = 0; i < 5; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            code = sb.toString();
        } while (taken.test(code));
        return code;
    }

    private Owner find(UUID ownerId) {
        return ownerRepository.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
    }
}
