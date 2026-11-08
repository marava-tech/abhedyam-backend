package com.abhedyam.service;

import com.abhedyam.dto.CreatePartnerRequest;
import com.abhedyam.dto.PartnerRow;
import com.abhedyam.dto.TrialCallRow;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.FounderCall;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Partner;
import com.abhedyam.model.PartnerEarning;
import com.abhedyam.repository.*;
import com.abhedyam.service.interfaces.IGrowthAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Spec: specs/billing.md ("Founder calls", "Partners"). */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrowthAdminService implements IGrowthAdminService {

    static final Set<String> STAGES = Set.of("DAY_3", "DAY_20", "ENDING", "ENDED");

    private final OwnerRepository ownerRepository;
    private final CustomerRepository customerRepository;
    private final SaleItemRepository saleItemRepository;
    private final FounderCallRepository callRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerEarningRepository earningRepository;

    /**
     * Which call is due for a non-paying shop, or null. Day 1 = signup day.
     * ENDED / ENDING first (money at stake), then the day-3 "any problem?" and day-20 "how is it going?" calls.
     */
    static String stage(long trialDay, boolean active, long daysLeft, long daysSinceEnd) {
        if (!active) return daysSinceEnd <= 7 ? "ENDED" : null;
        if (daysLeft <= 3) return "ENDING";
        if (trialDay >= 3 && trialDay <= 5) return "DAY_3";
        if (trialDay >= 20 && trialDay <= 22) return "DAY_20";
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrialCallRow> trialCalls() {
        Instant now = Instant.now();
        // ponytail: loads every non-paying shop from the last 60 days; page or query by date when that passes a few thousand
        List<Owner> owners = ownerRepository.findByPlayProductIdIsNullAndIsActiveTrueAndCreatedAtAfter(now.minus(60, ChronoUnit.DAYS));
        Map<UUID, List<FounderCall>> calls = new HashMap<>();
        if (!owners.isEmpty()) {
            for (FounderCall c : callRepository.findByOwnerIdInOrderByCreatedAtDesc(owners.stream().map(Owner::getId).toList())) {
                calls.computeIfAbsent(c.getOwnerId(), k -> new ArrayList<>()).add(c);
            }
        }
        List<TrialCallRow> rows = new ArrayList<>();
        for (Owner o : owners) {
            Instant until = o.getAccessUntil();
            boolean active = until != null && until.isAfter(now);
            long daysLeft = active ? (long) Math.ceil(Duration.between(now, until).toHours() / 24.0) : 0;
            long daysSinceEnd = until == null || active ? 0 : Duration.between(until, now).toDays();
            long trialDay = Duration.between(o.getCreatedAt(), now).toDays() + 1;
            String stage = stage(trialDay, active, daysLeft, daysSinceEnd);
            if (stage == null) continue;
            List<FounderCall> mine = calls.getOrDefault(o.getId(), List.of());
            Instant calledAt = mine.stream().filter(c -> c.getStage().equals(stage)).map(FounderCall::getCreatedAt).findFirst().orElse(null);
            String lastNote = mine.stream().map(FounderCall::getNote).filter(n -> n != null && !n.isBlank()).findFirst().orElse(null);
            rows.add(new TrialCallRow(o.getId(), o.getName(), o.getBusinessName(), o.getPhone(), o.getEmail(),
                    o.getCreatedAt(), trialDay, daysLeft, stage,
                    customerRepository.countByOwnerId(o.getId()),
                    saleItemRepository.countDistinctSalesByOwnerIdAndDateRange(o.getId(), null, null),
                    calledAt, lastNote));
        }
        // still-to-call first, then by urgency (ENDED, ENDING, DAY_3, DAY_20)
        List<String> order = List.of("ENDED", "ENDING", "DAY_3", "DAY_20");
        rows.sort(Comparator.comparing((TrialCallRow r) -> r.calledAt() != null).thenComparing(r -> order.indexOf(r.stage())));
        return rows;
    }

    @Override
    @Transactional
    public void logCall(UUID ownerId, String stage, String note) {
        if (!STAGES.contains(stage)) throw new BusinessException("INVALID_STAGE", "Stage must be one of " + STAGES);
        if (!ownerRepository.existsById(ownerId)) throw new ResourceNotFoundException("Owner not found");
        FounderCall call = new FounderCall();
        call.setOwnerId(ownerId);
        call.setStage(stage);
        call.setNote(note == null || note.isBlank() ? null : note.trim());
        callRepository.save(call);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerRow> partners() {
        return partnerRepository.findAllByOrderByCreatedAtDesc().stream().map(this::row).toList();
    }

    @Override
    @Transactional
    public PartnerRow createPartner(CreatePartnerRequest r) {
        Partner p = new Partner();
        p.setName(r.name().trim());
        p.setPhone(r.phone() == null || r.phone().isBlank() ? null : r.phone().trim());
        p.setUpiId(r.upiId() == null || r.upiId().isBlank() ? null : r.upiId().trim());
        p.setCode(ReferralService.newCode(c -> partnerRepository.existsByCode(c) || ownerRepository.existsByReferralCode(c)));
        partnerRepository.save(p);
        log.info("Partner created: {} code={}", p.getId(), p.getCode());
        return row(p);
    }

    @Override
    @Transactional
    public long payOut(UUID partnerId) {
        if (!partnerRepository.existsById(partnerId)) throw new ResourceNotFoundException("Partner not found");
        List<PartnerEarning> unpaid = earningRepository.findByPartnerIdAndPaidAtIsNull(partnerId);
        Instant now = Instant.now();
        unpaid.forEach(e -> e.setPaidAt(now));
        earningRepository.saveAll(unpaid);
        long total = unpaid.stream().mapToLong(PartnerEarning::getRupees).sum();
        log.info("Partner payout marked: partner={}, ₹{}, shops={}", partnerId, total, unpaid.size());
        return total;
    }

    private PartnerRow row(Partner p) {
        List<PartnerEarning> earnings = earningRepository.findByPartnerId(p.getId());
        long owed = earnings.stream().filter(e -> e.getPaidAt() == null).mapToLong(PartnerEarning::getRupees).sum();
        long paid = earnings.stream().filter(e -> e.getPaidAt() != null).mapToLong(PartnerEarning::getRupees).sum();
        return new PartnerRow(p.getId(), p.getName(), p.getPhone(), p.getUpiId(), p.getCode(),
                "https://duesetu.com/join?ref=" + p.getCode(), p.getCreatedAt(),
                ownerRepository.countByPartnerId(p.getId().toString()), earnings.size(), owed, paid);
    }
}
