package com.abhedyam.service;

import com.abhedyam.dto.CustomerLocationRequest;
import com.abhedyam.dto.CustomerLocationResponse;
import com.abhedyam.dto.LocationDetailsResponse;
import com.abhedyam.dto.LocationDetailsUpdateRequest;
import com.abhedyam.dto.VillageResponse;
import com.abhedyam.util.DistanceUtil;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.service.interfaces.ILocationDetailsService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.abhedyam.dto.PageResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationDetailsService implements ILocationDetailsService {

    private final LocationDetailsRepository locationDetailsRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final com.abhedyam.repository.SaleItemRepository saleItemRepository;
    private final com.abhedyam.repository.PaymentRepository paymentRepository;

    private static final String VILLAGES_CACHE_PREFIX = "villages:";

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public LocationDetailsResponse getCustomerLocation(UUID customerId) {
        UUID ownerId = SecurityUtil.getCurrentUserId();

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));

        if (customer.getOwnerId() == null || !customer.getOwnerId().equals(ownerId)) {
            throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this customer");
        }

        LocationDetails location = locationDetailsRepository.findByUserId(customerId).orElse(null);

        if (location == null) {
            return null;
        }

        return toResponse(location);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationDetailsResponse getLocationByUserId(UUID userId) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();

        if (userId.equals(currentUserId)) {
            LocationDetails location = locationDetailsRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("LocationDetails not found for user"));
            return toResponse(location);
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        if (currentUser.getType() == UserType.BUSINESS) {
            User targetUser = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User could not be found"));

            if (targetUser.getType() == UserType.CUSTOMER) {
                Customer customer = customerRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

                if (customer.getOwnerId() != null && customer.getOwnerId().equals(currentUserId)) {
                    LocationDetails location = locationDetailsRepository.findByUserId(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("LocationDetails not found for user"));
                    return toResponse(location);
                }
            }
        }

        throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this user's location");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VillageResponse> getVillagesPaginated(String name, Integer page, Integer size) {
        UUID ownerId = SecurityUtil.getCurrentUserId();

        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size < 1) {
            size = 20;
        }

        String normalizedName = name != null ? name.trim() : "";
        Pageable pageable = PageRequest.of(page, size);

        Page<Object[]> villagePage;
        if (normalizedName.isEmpty()) {
            villagePage = locationDetailsRepository.findVillagesWithCustomerCountByOwnerIdPageable(ownerId, pageable);
        } else {
            villagePage = locationDetailsRepository
                    .findVillagesWithCustomerCountByNameContainingIgnoreCaseAndOwnerIdPageable(
                            normalizedName, ownerId, pageable);
        }

        java.util.Map<java.util.UUID, java.math.BigDecimal> sales = toAmountMap(saleItemRepository.sumTotalsByCustomer(ownerId));
        java.util.Map<java.util.UUID, java.math.BigDecimal> paid = toAmountMap(paymentRepository.sumSuccessfulByCustomer(ownerId));
        java.util.Map<java.util.UUID, String> customerVillage = new java.util.HashMap<>();
        for (Object[] row : locationDetailsRepository.findCustomerVillagesByOwnerId(ownerId)) {
            customerVillage.put(java.util.UUID.fromString(row[0].toString()), (String) row[1]);
        }
        java.util.Map<String, java.math.BigDecimal> pendingByVillage =
                VillagePendingCalculator.pendingByVillage(sales, paid, customerVillage);

        List<VillageResponse> villages = villagePage.getContent().stream()
                .map(result -> {
                    String village = (String) result[0];
                    Long customerCount = ((Number) result[1]).longValue();
                    return new VillageResponse(village, customerCount,
                            pendingByVillage.getOrDefault(VillagePendingCalculator.key(village), java.math.BigDecimal.ZERO));
                })
                .toList();

        return new PageResponse<>(
                villages,
                villagePage.getNumber(),
                villagePage.getSize(),
                villagePage.getTotalElements(),
                villagePage.getTotalPages(),
                villagePage.hasNext(),
                villagePage.hasPrevious());
    }

    private static java.util.Map<java.util.UUID, java.math.BigDecimal> toAmountMap(List<Object[]> rows) {
        java.util.Map<java.util.UUID, java.math.BigDecimal> map = new java.util.HashMap<>();
        for (Object[] row : rows) {
            map.put(java.util.UUID.fromString(row[0].toString()), new java.math.BigDecimal(row[1].toString()));
        }
        return map;
    }

    @Transactional
    public LocationDetailsResponse updateCurrentUserLocation(LocationDetailsUpdateRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        LocationDetails location = locationDetailsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    LocationDetails newLocation = new LocationDetails();
                    newLocation.setId(userId);
                    newLocation.setUserId(userId);
                    return newLocation;
                });

        if (request.getLatitude() != null)
            location.setLatitude(request.getLatitude());
        if (request.getLongitude() != null)
            location.setLongitude(request.getLongitude());
        if (request.getVillage() != null)
            location.setVillage(request.getVillage());
        if (request.getAddressText() != null)
            location.setAddressText(request.getAddressText());

        LocationDetails saved = locationDetailsRepository.save(location);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LocationDetailsResponse updateLocationForUser(UUID userId, LocationDetailsUpdateRequest request) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        if (userId.equals(currentUserId)) {
            return updateCurrentUserLocation(request);
        }
        return updateCustomerLocation(userId, request);
    }

    @Override
    @Transactional
    public LocationDetailsResponse updateCustomerLocation(UUID customerId, LocationDetailsUpdateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));

        if (customer.getOwnerId() == null || !customer.getOwnerId().equals(ownerId)) {
            throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this customer");
        }

        LocationDetails location = locationDetailsRepository.findByUserId(customerId).orElse(null);

        if (location == null) {
            boolean hasPin = request.getLatitude() != null && request.getLongitude() != null;
            if (!hasPin && (request.getVillage() == null || request.getVillage().isBlank())) {
                throw new BusinessException("MISSING_REQUIRED_FIELDS",
                        "Send a map pin (latitude and longitude) or a village");
            }
            location = new LocationDetails();
            location.setId(customerId);
            location.setUserId(customerId);
            location.setLatitude(request.getLatitude());
            location.setLongitude(request.getLongitude());
            if (request.getVillage() != null) {
                location.setVillage(request.getVillage());
            }
            if (request.getAddressText() != null) {
                location.setAddressText(request.getAddressText());
            }
        } else {
            if (request.getLatitude() != null)
                location.setLatitude(request.getLatitude());
            if (request.getLongitude() != null)
                location.setLongitude(request.getLongitude());
            if (request.getVillage() != null)
                location.setVillage(request.getVillage());
            if (request.getAddressText() != null)
                location.setAddressText(request.getAddressText());
        }

        LocationDetails saved = locationDetailsRepository.save(location);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerLocationResponse> getCustomerLocations(CustomerLocationRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();

        List<LocationDetails> locations = locationDetailsRepository.findCustomerLocationsByCustomerIds(
                ownerId, request.getCustomerIds());

        List<CustomerLocationResponse> responses = locations.stream()
                .filter(location -> location.getLatitude() != null && location.getLongitude() != null)
                .map(location -> {
                    Customer customer = customerRepository.findById(location.getUserId()).orElse(null);
                    if (customer == null) {
                        return null;
                    }
                    return new CustomerLocationResponse(
                            location.getUserId(),
                            customer.getName(),
                            location.getLatitude(),
                            location.getLongitude());
                })
                .filter(response -> response != null)
                .collect(Collectors.toList());

        if (request.getCurrentLat() != null && request.getCurrentLng() != null) {
            responses.sort((r1, r2) -> {
                double distance1 = DistanceUtil.calculateDistanceKm(
                        request.getCurrentLat(), request.getCurrentLng(),
                        r1.getLat(), r1.getLng());
                double distance2 = DistanceUtil.calculateDistanceKm(
                        request.getCurrentLat(), request.getCurrentLng(),
                        r2.getLat(), r2.getLng());
                return Double.compare(distance1, distance2);
            });
        }

        return responses;
    }

    private LocationDetailsResponse toResponse(LocationDetails location) {
        LocationDetailsResponse response = new LocationDetailsResponse();
        response.setId(location.getId());
        response.setUserId(location.getUserId());
        // No pin is stored as NULL, but sent as 0 like before: released apps parse latitude as a required number
        // and already treat 0,0 as "no location".
        response.setLatitude(location.getLatitude() != null ? location.getLatitude() : java.math.BigDecimal.ZERO);
        response.setLongitude(location.getLongitude() != null ? location.getLongitude() : java.math.BigDecimal.ZERO);
        response.setVillage(location.getVillage());
        response.setAddressText(location.getAddressText());
        response.setCreatedAt(location.getCreatedAt());
        response.setUpdatedAt(location.getUpdatedAt());
        return response;
    }

    public void invalidateOwnerCaches(UUID ownerId) {
        try {
            var keys = redisTemplate.keys(VILLAGES_CACHE_PREFIX + ownerId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} village cache keys for owner {}", keys.size(), ownerId);
            }
        } catch (Exception e) {
            log.warn("Error invalidating village cache for owner {}: {}", ownerId, e.getMessage());
        }
    }
}
