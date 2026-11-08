package com.abhedyam.service;

import com.abhedyam.dto.ApplyReferralRequest;
import com.abhedyam.dto.ReferredOwnerProgressDto;
import com.abhedyam.dto.ReferralSummaryResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Payment;
import com.abhedyam.model.Referral;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.ReferralStatus;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ReferralRepository;
import com.abhedyam.repository.ReminderRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.ISubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // omits confusing I, O, 0, 1
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // Qualification thresholds
    public static final int REQUIRED_CUSTOMERS = 25;
    public static final int REQUIRED_ACTIVE_DAYS = 3;
    public static final int REQUIRED_TRANSACTIONS = 15;
    public static final BigDecimal REQUIRED_VOLUME = new BigDecimal("3000.00");
    public static final int REQUIRED_BILLS_SHARED = 2;

    private final OwnerRepository ownerRepository;
    private final ReferralRepository referralRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final SaleItemRepository saleItemRepository;
    private final ReminderRepository reminderRepository;
    private final ISubscriptionService subscriptionService;

    @Transactional
    public ReferralSummaryResponse getReferralSummary(UUID ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        String code = getOrCreateReferralCode(owner);
        String shareLink = "https://play.google.com/store/apps/details?id=tech.marava.abhedyam";

        List<Referral> referrals = referralRepository.findByReferrerOwnerIdOrderByCreatedAtDesc(ownerId);
        List<ReferredOwnerProgressDto> referralDtos = new ArrayList<>();

        long rewardedCount = 0;
        long pendingCount = 0;

        for (Referral ref : referrals) {
            if (ref.getStatus() == ReferralStatus.REWARDED || ref.getStatus() == ReferralStatus.QUALIFIED) {
                rewardedCount++;
            } else if (ref.getStatus() == ReferralStatus.PENDING) {
                pendingCount++;
            }

            String businessName = "Shop Owner";
            Optional<Owner> refereeOpt = ownerRepository.findById(ref.getRefereeOwnerId());
            if (refereeOpt.isPresent()) {
                String name = refereeOpt.get().getBusinessName();
                if (name != null && !name.trim().isEmpty()) {
                    businessName = name;
                }
            }

            int progress = calculateProgressPercentage(ref);

            referralDtos.add(ReferredOwnerProgressDto.builder()
                    .referralId(ref.getId())
                    .refereeOwnerId(ref.getRefereeOwnerId())
                    .businessName(businessName)
                    .status(ref.getStatus())
                    .createdAt(ref.getCreatedAt())
                    .customerCount(ref.getCustomerCount())
                    .requiredCustomers(REQUIRED_CUSTOMERS)
                    .activeDaysCount(ref.getActiveDaysCount())
                    .requiredActiveDays(REQUIRED_ACTIVE_DAYS)
                    .transactionCount(ref.getTransactionCount())
                    .requiredTransactions(REQUIRED_TRANSACTIONS)
                    .totalVolume(ref.getTotalVolume())
                    .requiredVolume(REQUIRED_VOLUME)
                    .billsSharedCount(ref.getBillsSharedCount())
                    .requiredBillsShared(REQUIRED_BILLS_SHARED)
                    .progressPercentage(progress)
                    .rewardedAt(ref.getRewardedAt())
                    .build());
        }

        return ReferralSummaryResponse.builder()
                .referralCode(code)
                .shareLink(shareLink)
                .totalReferred(referrals.size())
                .rewardedMonths(rewardedCount)
                .pendingCount(pendingCount)
                .referrals(referralDtos)
                .build();
    }

    @Transactional
    public void applyReferralCode(UUID refereeOwnerId, ApplyReferralRequest request) {
        String cleanCode = request.getReferralCode() != null ? request.getReferralCode().trim().toUpperCase() : "";
        if (cleanCode.isEmpty()) {
            throw new BusinessException("INVALID_CODE", "Please enter a valid referral code");
        }

        Owner referee = ownerRepository.findById(refereeOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        if (referee.getReferredBy() != null || referralRepository.existsByRefereeOwnerId(refereeOwnerId)) {
            throw new BusinessException("ALREADY_REFERRED", "You have already applied a referral code");
        }

        Owner referrer = ownerRepository.findByReferralCode(cleanCode)
                .orElseThrow(() -> new BusinessException("CODE_NOT_FOUND", "Referral code not found"));

        if (referrer.getId().equals(refereeOwnerId)) {
            throw new BusinessException("SELF_REFERRAL", "You cannot use your own referral code");
        }

        // Anti-fraud: device hardware fingerprint check
        String hardwareId = request.getDeviceHardwareId();
        if (hardwareId != null && !hardwareId.trim().isEmpty()) {
            referee.setDeviceHardwareId(hardwareId.trim());
            if (referrer.getDeviceHardwareId() != null && referrer.getDeviceHardwareId().equalsIgnoreCase(hardwareId.trim())) {
                log.warn("Self-referral attempt detected on same hardware: {}", hardwareId);
                throw new BusinessException("DEVICE_CONFLICT", "Referral not allowed on the same device");
            }
        }

        referee.setReferredBy(referrer.getId().toString());
        ownerRepository.save(referee);

        Referral referral = new Referral();
        referral.setReferrerOwnerId(referrer.getId());
        referral.setRefereeOwnerId(refereeOwnerId);
        referral.setStatus(ReferralStatus.PENDING);
        referral.setCustomerCount(0);
        referral.setActiveDaysCount(0);
        referral.setTransactionCount(0);
        referral.setTotalVolume(BigDecimal.ZERO);
        referral.setBillsSharedCount(0);
        referralRepository.save(referral);

        // Instant gratification: grant referee a 30-day welcome PRO boost!
        try {
            subscriptionService.grantPromotionalDays(refereeOwnerId, 30);
            log.info("Granted 30 welcome PRO days to referee {}", refereeOwnerId);
        } catch (Exception e) {
            log.error("Failed to grant welcome days to referee {}", refereeOwnerId, e);
        }

        log.info("Applied referral: referee {} referred by {} (code: {})", refereeOwnerId, referrer.getId(), cleanCode);
    }

    @Transactional
    public void evaluateReferral(Referral referral) {
        if (referral.getStatus() != ReferralStatus.PENDING) {
            return;
        }

        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        if (referral.getCreatedAt() != null && referral.getCreatedAt().isBefore(thirtyDaysAgo)) {
            referral.setStatus(ReferralStatus.REJECTED);
            referralRepository.save(referral);
            log.info("Referral {} expired past 30 days window", referral.getId());
            return;
        }

        UUID refereeId = referral.getRefereeOwnerId();

        // 1. Customer count
        long customerCount = customerRepository.countByOwnerId(refereeId);

        // 2. Payments & Sales for activity tracking
        List<Payment> payments = paymentRepository.findByOwnerId(refereeId);
        List<SaleItem> sales = saleItemRepository.findByOwnerId(refereeId);

        Set<LocalDate> distinctDates = new HashSet<>();
        BigDecimal volume = BigDecimal.ZERO;

        for (Payment p : payments) {
            if (p.getWriteOff() != null && p.getWriteOff()) continue;
            Instant ts = p.getTimestamp() != null ? p.getTimestamp() : p.getCreatedAt();
            if (ts != null) {
                distinctDates.add(ts.atZone(IST_ZONE).toLocalDate());
            }
            if (p.getAmount() != null) {
                volume = volume.add(p.getAmount());
            }
        }

        for (SaleItem s : sales) {
            Instant ts = s.getCreatedAt();
            if (ts != null) {
                distinctDates.add(ts.atZone(IST_ZONE).toLocalDate());
            }
            if (s.getPrice() != null && s.getQuantity() != null) {
                volume = volume.add(s.getPrice().multiply(s.getQuantity()));
            }
        }

        int txCount = payments.size() + sales.size();
        int activeDays = distinctDates.size();

        // 3. Outbound reminders/bills shared
        int remindersCount = reminderRepository.findByOwnerId(refereeId).size();

        referral.setCustomerCount((int) customerCount);
        referral.setActiveDaysCount(activeDays);
        referral.setTransactionCount(txCount);
        referral.setTotalVolume(volume);
        referral.setBillsSharedCount(remindersCount);

        // Check qualification
        boolean isQualified = customerCount >= REQUIRED_CUSTOMERS
                && activeDays >= REQUIRED_ACTIVE_DAYS
                && txCount >= REQUIRED_TRANSACTIONS
                && volume.compareTo(REQUIRED_VOLUME) >= 0;

        if (isQualified) {
            referral.setStatus(ReferralStatus.REWARDED);
            referral.setRewardedAt(Instant.now());
            referralRepository.save(referral);

            // Reward referrer with 30 days PRO!
            subscriptionService.grantPromotionalDays(referral.getReferrerOwnerId(), 30);
            log.info("🎉 Referral {} QUALIFIED & REWARDED! Referrer {} rewarded 30 PRO days.",
                    referral.getId(), referral.getReferrerOwnerId());
        } else {
            referralRepository.save(referral);
        }
    }

    @Transactional
    public int evaluateAllPendingReferrals() {
        List<Referral> pending = referralRepository.findByStatus(ReferralStatus.PENDING);
        int evaluated = 0;
        for (Referral r : pending) {
            try {
                evaluateReferral(r);
                evaluated++;
            } catch (Exception e) {
                log.error("Error evaluating referral {}", r.getId(), e);
            }
        }
        return evaluated;
    }

    public String getOrCreateReferralCode(Owner owner) {
        if (owner.getReferralCode() != null && !owner.getReferralCode().trim().isEmpty()) {
            return owner.getReferralCode();
        }

        String code = generateUniqueCode();
        owner.setReferralCode(code);
        ownerRepository.save(owner);
        return code;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder("ABH");
            for (int j = 0; j < 5; j++) {
                sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
            }
            String candidate = sb.toString();
            if (!ownerRepository.existsByReferralCode(candidate)) {
                return candidate;
            }
        }
        return "ABH" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
    }

    private int calculateProgressPercentage(Referral ref) {
        if (ref.getStatus() == ReferralStatus.REWARDED || ref.getStatus() == ReferralStatus.QUALIFIED) {
            return 100;
        }
        double customerScore = Math.min(1.0, (double) ref.getCustomerCount() / REQUIRED_CUSTOMERS);
        double daysScore = Math.min(1.0, (double) ref.getActiveDaysCount() / REQUIRED_ACTIVE_DAYS);
        double txScore = Math.min(1.0, (double) ref.getTransactionCount() / REQUIRED_TRANSACTIONS);
        double volumeScore = Math.min(1.0, ref.getTotalVolume().doubleValue() / REQUIRED_VOLUME.doubleValue());

        double totalScore = (customerScore * 0.35) + (daysScore * 0.35) + (txScore * 0.15) + (volumeScore * 0.15);
        return (int) Math.round(totalScore * 100);
    }
}
