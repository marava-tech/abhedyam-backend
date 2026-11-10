package com.abhedyam.service;

import com.abhedyam.dto.OwnerResponse;
import com.abhedyam.dto.OwnerSummaryResponse;
import com.abhedyam.dto.OwnerUpdateRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.IOwnerService;
import com.abhedyam.util.EmailUtil;
import com.abhedyam.util.PhoneUtil;
import com.abhedyam.util.SecurityUtil;
import com.abhedyam.constants.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerService implements IOwnerService {
    
    private final OwnerRepository ownerRepository;
    private final LocationDetailsRepository locationDetailsRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final SaleItemRepository saleItemRepository;
    
    public OwnerResponse getById(UUID id) {
        Owner owner = ownerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account could not be found"));
        return toResponse(owner);
    }
    
    @Transactional
    public OwnerResponse updateCurrentOwner(OwnerUpdateRequest request) {
        UUID id = SecurityUtil.getCurrentUserId();
        Owner owner = ownerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account could not be found"));
        
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            owner.setName(request.getName());
        }
        
        if (request.getBusinessName() != null && !request.getBusinessName().trim().isEmpty()) {
            owner.setBusinessName(request.getBusinessName());
        }
        
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String normalizedPhone = PhoneUtil.normalizePhone(request.getPhone());
            owner.setPhone(PhoneUtil.extractPhoneWithoutCountryCode(normalizedPhone));
            owner.setPhoneNormalized(normalizedPhone);
        }
        
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            owner.setEmail(EmailUtil.normalizeEmail(request.getEmail()));
        }
        
        if (request.getImageUrl() != null) {
            if (request.getImageUrl().trim().isEmpty()) {
                owner.setImageUrl(null);
            } else {
                owner.setImageUrl(request.getImageUrl());
            }
        }
        
        if (request.getIsVerified() != null) {
            owner.setIsVerified(request.getIsVerified());
        }
        
        if (request.getPublicListingEnabled() != null) {
            owner.setPublicListingEnabled(request.getPublicListingEnabled());
        }

        if (request.getPublicSlug() != null) {
            String slug = request.getPublicSlug().trim().toLowerCase();
            if (slug.isEmpty()) {
                owner.setPublicSlug(null);
            } else {
                if (!slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$") || slug.length() > 80) {
                    throw new BusinessException("INVALID_SLUG", "Shop slug must be lowercase letters, digits and hyphens");
                }
                ownerRepository.findByPublicSlug(slug).ifPresent(existing -> {
                    if (!existing.getId().equals(owner.getId())) {
                        throw new BusinessException("SLUG_TAKEN", "That shop URL is already in use");
                    }
                });
                owner.setPublicSlug(slug);
            }
        }
        
        Owner saved = ownerRepository.save(owner);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public OwnerResponse updateOwnerForOwner(UUID ownerId, OwnerUpdateRequest request) {
        validateOwnerAccess(ownerId);
        return updateCurrentOwner(request);
    }
    
    private OwnerResponse toResponse(Owner owner) {
        OwnerResponse response = new OwnerResponse();
        response.setId(owner.getId());
        response.setName(owner.getName());
        response.setBusinessName(owner.getBusinessName());
        response.setPhone(owner.getPhone());
        response.setEmail(owner.getEmail());
        response.setType(owner.getType());
        response.setImageUrl(owner.getImageUrl());
        response.setIsVerified(owner.getIsVerified());
        response.setPublicSlug(owner.getPublicSlug());
        response.setPublicListingEnabled(owner.getPublicListingEnabled());
        response.setCreatedAt(owner.getCreatedAt());
        response.setUpdatedAt(owner.getUpdatedAt());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerSummaryResponse getOwnerSummary(UUID ownerId) {
        validateOwnerAccess(ownerId);
        
        long totalCustomers = customerRepository.countByOwnerId(ownerId);
        
        BigDecimal totalSalesAmount = saleItemRepository.sumTotalsByCustomer(ownerId).stream()
            .map(r -> new BigDecimal(r[1].toString()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Balance counts write-offs (they close the due); "collected" does not (no money came in).
        BigDecimal settledAmount = paymentRepository.sumSuccessfulByCustomer(ownerId).stream()
            .map(r -> new BigDecimal(r[1].toString()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollectedAmount = paymentRepository.sumCollected(ownerId);

        BigDecimal totalPendingAmount = totalSalesAmount.subtract(settledAmount);
        if (totalPendingAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalPendingAmount = BigDecimal.ZERO;
        }
        
        long totalVillages = locationDetailsRepository.countVillagesByOwnerId(ownerId);
        
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.ZonedDateTime monthStart = java.time.LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone);
        BigDecimal collectedThisMonth = paymentRepository.sumSuccessfulBetween(
            ownerId, monthStart.toInstant(), monthStart.plusMonths(1).toInstant());
        // In SQL: Home used to add up only the 10 newest payments it had loaded.
        java.time.ZonedDateTime dayStart = java.time.LocalDate.now(zone).atStartOfDay(zone);
        java.time.Instant dayEnd = dayStart.plusDays(1).toInstant();
        WeekWindow week = WeekWindow.at(java.time.ZonedDateTime.now(zone));

        return new OwnerSummaryResponse(
            totalPendingAmount,
            totalCustomers,
            totalVillages,
            totalCollectedAmount,
            collectedThisMonth,
            paymentRepository.sumSuccessfulBetween(ownerId, dayStart.toInstant(), dayEnd),
            paymentRepository.countSuccessfulBetween(ownerId, dayStart.toInstant(), dayEnd),
            paymentRepository.sumSuccessfulBetween(ownerId, week.thisStart(), week.now()),
            paymentRepository.sumSuccessfulBetween(ownerId, week.lastStart(), week.lastSameTime())
        );
    }
    
    private void validateOwnerAccess(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        if (ownerId == null || !ownerId.equals(currentOwnerId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only access your own owner profile");
        }
    }

    /** This week (Monday 00:00 → now) and the same stretch last week, so Tuesday is compared with Tuesday. */
    record WeekWindow(java.time.Instant thisStart, java.time.Instant now, java.time.Instant lastStart, java.time.Instant lastSameTime) {
        static WeekWindow at(java.time.ZonedDateTime now) {
            java.time.ZonedDateTime monday = now.toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay(now.getZone());
            return new WeekWindow(monday.toInstant(), now.toInstant(),
                monday.minusWeeks(1).toInstant(), now.minusWeeks(1).toInstant());
        }
    }
}
