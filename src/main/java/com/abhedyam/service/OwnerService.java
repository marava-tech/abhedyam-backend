package com.abhedyam.service;

import com.abhedyam.dto.OwnerPublicResponse;
import com.abhedyam.dto.OwnerResponse;
import com.abhedyam.dto.OwnerSummaryResponse;
import com.abhedyam.dto.OwnerUpdateRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.model.Owner;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSettingsRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.UPIAccountRepository;
import com.abhedyam.service.interfaces.IOwnerService;
import com.abhedyam.util.EmailUtil;
import com.abhedyam.util.PhoneUtil;
import com.abhedyam.util.SecurityUtil;
import com.abhedyam.constants.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerService implements IOwnerService {
    
    private final OwnerRepository ownerRepository;
    private final LocationDetailsRepository locationDetailsRepository;
    private final OwnerSettingsRepository ownerSettingsRepository;
    private final UPIAccountRepository upiAccountRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final SaleItemRepository saleItemRepository;
    
    public OwnerResponse getById(UUID id) {
        Owner owner = ownerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account could not be found"));
        return toResponse(owner);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<OwnerPublicResponse> getAllPublic(BigDecimal latitude, BigDecimal longitude) {
        List<Owner> owners = ownerRepository.findAll();
        
        List<UUID> ownerIds = owners.stream().map(Owner::getId).collect(Collectors.toList());
        List<LocationDetails> locations = locationDetailsRepository.findByUserIdIn(ownerIds);
        Map<UUID, LocationDetails> locationMap = locations.stream()
            .collect(Collectors.toMap(LocationDetails::getUserId, loc -> loc, (v1, v2) -> v1));
        
        List<OwnerPublicResponse> responses = owners.stream()
                .map(owner -> {
                    LocationDetails location = locationMap.get(owner.getId());
                    
                    OwnerPublicResponse response = new OwnerPublicResponse();
                    response.setId(owner.getId());
                    response.setName(owner.getName());
                    response.setBusinessName(owner.getBusinessName());
                    response.setPhone(owner.getPhone());
                    response.setEmail(owner.getEmail());
                    response.setImageUrl(owner.getImageUrl());
                    response.setIsVerified(owner.getIsVerified());
                    if (Boolean.TRUE.equals(owner.getPublicListingEnabled())) {
                        response.setPublicSlug(owner.getPublicSlug());
                    }
                    
                    if (location != null) {
                        response.setLatitude(location.getLatitude());
                        response.setLongitude(location.getLongitude());
                        response.setVillage(location.getVillage());
                        response.setAddressText(location.getAddressText());
                        
                        if (latitude != null && longitude != null && 
                            location.getLatitude() != null && location.getLongitude() != null) {
                            BigDecimal distance = calculateDistance(
                                latitude, longitude,
                                location.getLatitude(), location.getLongitude()
                            );
                            response.setDistance(distance);
                        }
                    }
                    
                    return response;
                })
                .collect(Collectors.toList());
        
        if (latitude != null && longitude != null) {
            responses.sort(Comparator
                .comparing((OwnerPublicResponse r) -> r.getDistance() != null ? 0 : 1)
                .thenComparing((OwnerPublicResponse r) -> r.getDistance(), 
                    Comparator.nullsLast(Comparator.naturalOrder()))
            );
        } else {
            responses.sort(Comparator.comparing(
                (OwnerPublicResponse r) -> r.getLatitude() != null && r.getLongitude() != null ? 0 : 1
            ));
        }
        
        return responses;
    }
    
    private BigDecimal calculateDistance(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        final int EARTH_RADIUS_KM = 6371;
        
        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lon1Rad = Math.toRadians(lon1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double lon2Rad = Math.toRadians(lon2.doubleValue());
        
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = EARTH_RADIUS_KM * c;
        
        return BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
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
        
        if (request.getSubscription() != null) {
            owner.setSubscription(request.getSubscription());
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
        response.setSubscription(owner.getSubscription());
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
        BigDecimal totalCollectedAmount = paymentRepository.sumSuccessfulByCustomer(ownerId).stream()
            .map(r -> new BigDecimal(r[1].toString()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalPendingAmount = totalSalesAmount.subtract(totalCollectedAmount);
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
