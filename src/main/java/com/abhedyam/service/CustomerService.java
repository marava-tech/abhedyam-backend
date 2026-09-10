package com.abhedyam.service;

import com.abhedyam.dto.CustomerCreateRequest;
import com.abhedyam.dto.CustomerBasicSummaryResponse;
import com.abhedyam.dto.CustomerResponse;
import com.abhedyam.dto.CustomerNotesSummaryResponse;
import com.abhedyam.dto.CustomerPaymentsSummaryResponse;
import com.abhedyam.dto.CustomerRemindersSummaryResponse;
import com.abhedyam.dto.CustomerSalesSummaryResponse;
import com.abhedyam.dto.CustomerSearchRequest;
import com.abhedyam.dto.CustomerSearchResult;
import com.abhedyam.dto.CustomerSummaryResponse;
import com.abhedyam.dto.CustomerUpdateRequest;
import com.abhedyam.dto.NearestCustomerRequest;
import com.abhedyam.dto.NearestCustomerResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.model.Payment;
import com.abhedyam.model.Product;
import com.abhedyam.model.Reminder;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.model.enums.ReminderStatus;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.NoteRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.ReminderRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.service.interfaces.ICustomerService;
import com.abhedyam.util.PhoneUtil;
import com.abhedyam.util.SecurityUtil;
import com.abhedyam.constants.CacheKeys;
import com.abhedyam.constants.ErrorCodes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService implements ICustomerService {
    
    private final CustomerRepository customerRepository;
    private final LocationDetailsRepository locationDetailsRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final NoteRepository noteRepository;
    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final StatsService statsService;
    private final LocationDetailsService locationDetailsService;
    
    @Autowired
    @Qualifier("virtualThreadExecutor")
    private Executor virtualThreadExecutor;
    
    private static final int CACHE_TTL_MINUTES = 5;
    
    @Override
    @Transactional
    public Customer create(CustomerCreateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        boolean hasPhone = request.getPhone() != null && !request.getPhone().trim().isEmpty();
        boolean hasVillage = request.getVillage() != null && !request.getVillage().trim().isEmpty();

        if (hasPhone) {
            String normalizedPhone = PhoneUtil.normalizePhone(request.getPhone());
            Optional<Customer> existingCustomerOpt = customerRepository.findByPhoneNormalized(normalizedPhone);
            if (existingCustomerOpt.isPresent()) {
                Customer existingCustomer = existingCustomerOpt.get();
                if (existingCustomer.getOwnerId() == null) {
                    customerRepository.delete(existingCustomer);
                    customerRepository.flush();
                } else {
                    throw new BusinessException(ErrorCodes.CUSTOMER_EXISTS,
                        "Customer with this phone number already exists and is associated with another owner");
                }
            }
        } else if (hasVillage) {
            Optional<Customer> existingCustomerOpt = customerRepository.findByOwnerIdAndNameAndVillageIgnoreCase(
                ownerId, request.getName().trim(), request.getVillage().trim());
            if (existingCustomerOpt.isPresent()) {
                throw new BusinessException(ErrorCodes.CUSTOMER_EXISTS,
                    "Customer with this name and village already exists");
            }
        }

        Customer customer = new Customer();
        customer.setName(request.getName());
        if (hasPhone) {
            String normalizedPhone = PhoneUtil.normalizePhone(request.getPhone());
            customer.setPhone(PhoneUtil.extractPhoneWithoutCountryCode(normalizedPhone));
            customer.setPhoneNormalized(normalizedPhone);
        }
        customer.setType(UserType.CUSTOMER);
        customer.setOwnerId(ownerId);
        customer.setIsActive(true);
        
        Customer savedCustomer = customerRepository.save(customer);
        
        invalidateAllRelatedCachesOnCustomerChange(ownerId);
        
        LocationDetails existingLocation = locationDetailsRepository.findById(savedCustomer.getId()).orElse(null);
        String villageName = (request.getVillage() != null && !request.getVillage().trim().isEmpty()) 
            ? request.getVillage().trim() 
            : "No Village";
        
        if (existingLocation != null) {
            existingLocation.setVillage(villageName);
            if (request.getLatitude() != null) {
                existingLocation.setLatitude(request.getLatitude());
            }
            if (request.getLongitude() != null) {
                existingLocation.setLongitude(request.getLongitude());
            }
            locationDetailsRepository.save(existingLocation);
        } else {
            LocationDetails locationDetails = new LocationDetails();
            locationDetails.setUserId(savedCustomer.getId());
            locationDetails.setVillage(villageName);
            locationDetails.setLatitude(request.getLatitude() != null ? request.getLatitude() : BigDecimal.ZERO);
            locationDetails.setLongitude(request.getLongitude() != null ? request.getLongitude() : BigDecimal.ZERO);
            locationDetailsRepository.save(locationDetails);
        }
        
        return savedCustomer;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Customer getById(UUID id) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        
        // Allow access if:
        // 1. Current user is the customer themselves
        // 2. Current user is the owner of this customer
        if (currentUserId.equals(id)) {
            return customer;
        }
        
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Your account could not be found"));
        
        if (currentUser.getType() == UserType.BUSINESS && 
            customer.getOwnerId() != null && 
            customer.getOwnerId().equals(currentUserId)) {
            return customer;
        }
        
        throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this customer");
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Customer> getByOwnerId(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        UUID targetOwnerId = ownerId != null ? ownerId : currentOwnerId;
        if (!currentOwnerId.equals(targetOwnerId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only access your own customers");
        }
        return customerRepository.findByOwnerId(targetOwnerId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getMyCustomersWithVillage(Integer page, Integer size) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        
        String cacheKey = CacheKeys.CUSTOMERS_MY_PREFIX + ownerId + ":" + page + ":" + size;
        
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                PageResponse<CustomerResponse> cachedResponse = objectMapper.readValue(
                    cached, 
                    new TypeReference<PageResponse<CustomerResponse>>() {}
                );
                log.debug("Returning cached customers for owner {} page {} size {}", ownerId, page, size);
                return cachedResponse;
            }
        } catch (Exception e) {
            log.warn("Error reading from cache for key: {}", cacheKey, e);
        }
        
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Customer> customerPage = customerRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
        List<Customer> customers = customerPage.getContent();
        
        List<UUID> customerIds = customers.stream().map(Customer::getId).toList();
        List<LocationDetails> locations = locationDetailsRepository.findByUserIdIn(customerIds);
        java.util.Map<UUID, String> villageMap = locations.stream()
            .collect(java.util.stream.Collectors.toMap(
                LocationDetails::getUserId,
                LocationDetails::getVillage,
                (v1, v2) -> v1
            ));
        
        List<CustomerResponse> responses = customers.stream()
            .map(customer -> {
                String village = villageMap.get(customer.getId());
                return CustomerResponse.fromEntity(customer, village);
            })
            .toList();
        
        PageResponse<CustomerResponse> response = new PageResponse<>(
            responses,
            customerPage.getNumber(),
            customerPage.getSize(),
            customerPage.getTotalElements(),
            customerPage.getTotalPages(),
            customerPage.hasNext(),
            customerPage.hasPrevious()
        );
        
        try {
            String jsonResponse = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonResponse, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached customers for owner {} page {} size {}", ownerId, page, size);
        } catch (Exception e) {
            log.warn("Error caching customers for key: {}", cacheKey, e);
        }
        
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getOwnerCustomers(UUID ownerId, String searchText, String village, Integer page, Integer size, String sortBy, String sortDirection, boolean includePendingAmountDetails) {
        validateOwnerAccess(ownerId);

        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size < 1) {
            size = 20;
        }

        String normalizedSearchText = searchText != null && !searchText.trim().isEmpty() ? searchText.trim() : null;
        String normalizedVillage = village != null && !village.trim().isEmpty() ? village.trim() : null;
        boolean isNumeric = false;
        if (normalizedSearchText != null) {
            try {
                Long.parseLong(normalizedSearchText);
                isNumeric = true;
            } catch (NumberFormatException e) {
                isNumeric = false;
            }
        }

        // Default ordering is by village (grouped, then by name, blanks last) —
        // shopkeepers work village-by-village. Any other sortBy keeps the old
        // Customer-column behaviour.
        String effectiveSortBy = sortBy != null && !sortBy.trim().isEmpty() ? sortBy.trim() : "village";
        boolean sortByVillage = "village".equalsIgnoreCase(effectiveSortBy);

        Page<Customer> customerPage;
        if (sortByVillage) {
            Pageable pageable = PageRequest.of(page, size); // ORDER BY lives in the query
            if (normalizedVillage != null) {
                customerPage = customerRepository.searchCustomersWithVillageFilterOrderByVillage(
                    ownerId,
                    normalizedVillage,
                    normalizedSearchText,
                    isNumeric,
                    pageable
                );
            } else {
                customerPage = customerRepository.searchCustomersOrderByVillage(
                    ownerId,
                    normalizedSearchText,
                    isNumeric,
                    pageable
                );
            }
        } else {
            Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(sortDirection)
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC,
                effectiveSortBy
            );
            Pageable pageable = PageRequest.of(page, size, sort);

            if (normalizedVillage != null) {
                customerPage = customerRepository.searchCustomersWithVillageFilter(
                    ownerId,
                    normalizedVillage,
                    normalizedSearchText,
                    isNumeric,
                    pageable
                );
            } else {
                customerPage = customerRepository.searchCustomersWithVillage(
                    ownerId,
                    normalizedSearchText,
                    isNumeric,
                    pageable
                );
            }
        }

        List<Customer> customers = customerPage.getContent();
        List<UUID> customerIds = customers.stream().map(Customer::getId).toList();
        List<LocationDetails> locations = locationDetailsRepository.findByUserIdIn(customerIds);
        Map<UUID, String> villageMap = locations.stream()
            .collect(Collectors.toMap(
                LocationDetails::getUserId,
                LocationDetails::getVillage,
                (v1, v2) -> v1
            ));

        List<CustomerResponse> responses = customers.stream()
            .map(customer -> {
                String customerVillage = villageMap.get(customer.getId());
                return CustomerResponse.fromEntity(customer, customerVillage);
            })
            .toList();

        if (includePendingAmountDetails && !customerIds.isEmpty()) {
            List<SaleItem> saleItems = saleItemRepository.findByCustomerIdAndOwnerIdIn(customerIds, ownerId);
            List<Payment> payments = paymentRepository.findByCustomerIdInAndOwnerId(customerIds, ownerId);
            Map<UUID, BigDecimal> totalByCustomer = saleItems.stream()
                .collect(Collectors.groupingBy(
                    SaleItem::getCustomerId,
                    Collectors.reducing(
                        BigDecimal.ZERO,
                        si -> si.getPrice().multiply(si.getQuantity() != null ? si.getQuantity() : BigDecimal.ONE),
                        BigDecimal::add
                    )
                ));
            Map<UUID, BigDecimal> paidByCustomer = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .collect(Collectors.groupingBy(
                    Payment::getCustomerId,
                    Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)
                ));
            for (CustomerResponse r : responses) {
                BigDecimal total = totalByCustomer.getOrDefault(r.getId(), BigDecimal.ZERO);
                BigDecimal paid = paidByCustomer.getOrDefault(r.getId(), BigDecimal.ZERO);
                r.setPendingAmount(total.subtract(paid));
            }
        }

        return new PageResponse<>(
            responses,
            customerPage.getNumber(),
            customerPage.getSize(),
            customerPage.getTotalElements(),
            customerPage.getTotalPages(),
            customerPage.hasNext(),
            customerPage.hasPrevious()
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> filterCustomers(String searchText) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        String normalizedSearchText = searchText != null && !searchText.trim().isEmpty() ? searchText.trim() : "";
        
        boolean isNumeric = false;
        
        if (!normalizedSearchText.isEmpty()) {
            try {
                Long.parseLong(normalizedSearchText);
                isNumeric = true;
            } catch (NumberFormatException e) {
                isNumeric = false;
            }
        }
        
        List<Customer> customers = customerRepository.filterCustomers(
            ownerId,
            normalizedSearchText.isEmpty() ? null : normalizedSearchText,
            isNumeric
        );
        
        List<Customer> limitedCustomers = customers.stream().limit(15).toList();
        List<UUID> customerIds = limitedCustomers.stream().map(Customer::getId).toList();
        List<LocationDetails> locations = locationDetailsRepository.findByUserIdIn(customerIds);
        java.util.Map<UUID, String> villageMap = locations.stream()
            .collect(java.util.stream.Collectors.toMap(
                LocationDetails::getUserId,
                LocationDetails::getVillage,
                (v1, v2) -> v1
            ));
        
        return limitedCustomers.stream()
            .map(customer -> {
                String village = villageMap.get(customer.getId());
                return CustomerResponse.fromEntity(customer, village);
            })
            .toList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<Customer> searchCustomers(CustomerSearchRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        Sort sort = Sort.by(
            "DESC".equalsIgnoreCase(request.getSortDirection()) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC,
            request.getSortBy()
        );
        
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        
        Page<Customer> page = customerRepository.searchCustomers(
            ownerId,
            request.getSearchTerm(),
            pageable
        );
        
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext(),
            page.hasPrevious()
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<CustomerSearchResult> searchByName(String name) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        List<Customer> customers = customerRepository.findByNameContainingIgnoreCaseAndOwnerId(name, ownerId);
        
        if (customers.isEmpty()) {
            return List.of();
        }
        
        List<UUID> customerIds = customers.stream().map(Customer::getId).toList();
        List<LocationDetails> locations = locationDetailsRepository.findByUserIdIn(customerIds);
        java.util.Map<UUID, String> villageMap = locations.stream()
            .collect(java.util.stream.Collectors.toMap(
                LocationDetails::getUserId,
                LocationDetails::getVillage,
                (v1, v2) -> v1
            ));
        
        return customers.stream()
            .map(customer -> {
                String village = villageMap.get(customer.getId());
                return new CustomerSearchResult(customer.getId(), customer.getName(), customer.getPhone(), village);
            })
            .toList();
    }
    
    @Override
    @Transactional
    public Customer updateCustomer(CustomerUpdateRequest request) {
        Customer customer = getById(request.getId());
        return updateCustomerInternal(customer, request);
    }

    public void invalidateOwnerCaches(UUID ownerId) {
        try {
            String[] patterns = {
                CacheKeys.CUSTOMERS_MY_PREFIX + ownerId + ":*",
                CacheKeys.CUSTOMER_SUMMARY_PREFIX + ownerId + ":*",
                CacheKeys.CUSTOMER_MY_SUMMARY_PREFIX + "*"
            };
            
            for (String pattern : patterns) {
                try {
                    var keys = redisTemplate.keys(pattern);
                    if (keys != null && !keys.isEmpty()) {
                        redisTemplate.delete(keys);
                        log.debug("Invalidated {} customer cache keys for pattern: {}", keys.size(), pattern);
                    }
                } catch (Exception e) {
                    log.warn("Error invalidating customer cache for pattern {}: {}", pattern, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error during customer cache invalidation for owner {}: {}", ownerId, e.getMessage());
        }
    }
    
    private void invalidateAllRelatedCachesOnCustomerChange(UUID ownerId) {
        try {
            invalidateOwnerCaches(ownerId);
            
            try {
                var paymentKeys = redisTemplate.keys(CacheKeys.PAYMENTS_MY_PREFIX + ownerId + ":*");
                if (paymentKeys != null && !paymentKeys.isEmpty()) {
                    redisTemplate.delete(paymentKeys);
                    log.debug("Invalidated {} payment cache keys for owner {}", paymentKeys.size(), ownerId);
                }
            } catch (Exception e) {
                log.warn("Error invalidating payment cache for owner {}: {}", ownerId, e.getMessage());
            }
            
            statsService.invalidateOwnerCaches(ownerId);
            locationDetailsService.invalidateOwnerCaches(ownerId);
            log.debug("Completed cache invalidation for owner {} on customer change", ownerId);
        } catch (Exception e) {
            log.warn("Error during cache invalidation on customer change for owner {}: {}", ownerId, e.getMessage());
        }
    }

    @Override
    public void invalidateCustomerSummaryCache(UUID ownerId, UUID customerId) {
        try {
            if (ownerId != null && customerId != null) {
                redisTemplate.delete(CacheKeys.CUSTOMER_SUMMARY_PREFIX + ownerId + ":" + customerId);
                redisTemplate.delete(CacheKeys.CUSTOMER_MY_SUMMARY_PREFIX + customerId);
            }
        } catch (Exception e) {
            log.warn("Error invalidating customer summary cache for owner {} customer {}: {}", ownerId, customerId, e.getMessage());
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public NearestCustomerResponse findNearestCustomer(NearestCustomerRequest request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new BusinessException(ErrorCodes.MISSING_LOCATION, "Please provide your current location");
        }
        
        UUID ownerId = SecurityUtil.getCurrentUserId();
        BigDecimal ownerLat = request.getLatitude();
        BigDecimal ownerLng = request.getLongitude();
        
        List<LocationDetails> allLocationDetails;
        if (request.getVillage() != null && !request.getVillage().trim().isEmpty()) {
            String village = request.getVillage().trim();
            log.debug("Filtering customers by village: {}", village);
            allLocationDetails = locationDetailsRepository.findCustomerLocationsByOwnerIdAndVillage(ownerId, village);
            
            if (allLocationDetails.isEmpty()) {
                throw new ResourceNotFoundException("No customers found in " + village);
            }
        } else {
            allLocationDetails = locationDetailsRepository.findCustomerLocationsByOwnerId(ownerId);
            
            if (allLocationDetails.isEmpty()) {
                throw new ResourceNotFoundException("No customers found with location information");
            }
        }
        
        List<UUID> customerIdsWithLocation = allLocationDetails.stream()
            .map(LocationDetails::getUserId)
            .collect(Collectors.toList());
        
        CompletableFuture<List<SaleItem>> saleItemsFuture = CompletableFuture.supplyAsync(
            () -> saleItemRepository.findByCustomerIdAndOwnerIdIn(customerIdsWithLocation, ownerId),
            virtualThreadExecutor
        );
        
        CompletableFuture<List<Payment>> paymentsFuture = CompletableFuture.supplyAsync(
            () -> paymentRepository.findByCustomerIdInAndOwnerId(customerIdsWithLocation, ownerId),
            virtualThreadExecutor
        );
        
        CompletableFuture.allOf(saleItemsFuture, paymentsFuture).join();
        
        List<SaleItem> saleItems = saleItemsFuture.join();
        List<Payment> payments = paymentsFuture.join();
        
        Map<UUID, BigDecimal> totalAmountByCustomer = saleItems.parallelStream()
            .collect(Collectors.groupingBy(
                SaleItem::getCustomerId,
                Collectors.reducing(
                    BigDecimal.ZERO,
                    item -> item.getPrice().multiply(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE),
                    BigDecimal::add
                )
            ));
        
        Map<UUID, BigDecimal> totalPaidByCustomer = payments.parallelStream()
            .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
            .collect(Collectors.groupingBy(
                Payment::getCustomerId,
                Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)
            ));
        
        List<UUID> customersWithPendingAmounts = customerIdsWithLocation.parallelStream()
            .filter(customerId -> {
                BigDecimal totalAmount = totalAmountByCustomer.getOrDefault(customerId, BigDecimal.ZERO);
                BigDecimal totalPaid = totalPaidByCustomer.getOrDefault(customerId, BigDecimal.ZERO);
                return totalAmount.subtract(totalPaid).compareTo(BigDecimal.ZERO) > 0;
            })
            .collect(Collectors.toList());
        
        if (customersWithPendingAmounts.isEmpty()) {
            throw new ResourceNotFoundException("No customers with location details and pending amount found");
        }
        
        List<LocationDetails> locationDetails = locationDetailsRepository.findCustomerLocationsByCustomerIds(ownerId, customersWithPendingAmounts);
        
        Optional<LocationDetailsWithDistance> nearestLocationOpt = locationDetails.parallelStream()
            .map(loc -> {
                double distance = calculateDistance(
                    ownerLat.doubleValue(),
                    ownerLng.doubleValue(),
                    loc.getLatitude().doubleValue(),
                    loc.getLongitude().doubleValue()
                );
                return new LocationDetailsWithDistance(loc, distance);
            })
            .min(Comparator.comparingDouble(LocationDetailsWithDistance::getDistance));
        
        if (nearestLocationOpt.isEmpty()) {
            throw new ResourceNotFoundException("No customers with location details and pending amount found");
        }
        
        LocationDetailsWithDistance nearestLocationWithDistance = nearestLocationOpt.get();
        LocationDetails nearestLocation = nearestLocationWithDistance.getLocation();
        double distanceKm = nearestLocationWithDistance.getDistance();
        UUID nearestCustomerId = nearestLocation.getUserId();
        
        CompletableFuture<Customer> customerFuture = CompletableFuture.supplyAsync(
            () -> customerRepository.findById(nearestCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found")),
            virtualThreadExecutor
        );
        
        List<SaleItem> customerSaleItems = saleItems.parallelStream()
            .filter(item -> item.getCustomerId().equals(nearestCustomerId))
            .collect(Collectors.toList());
        List<Payment> customerPayments = payments.parallelStream()
            .filter(p -> p.getCustomerId().equals(nearestCustomerId))
            .collect(Collectors.toList());
        
        BigDecimal totalAmount = totalAmountByCustomer.getOrDefault(nearestCustomerId, BigDecimal.ZERO);
        BigDecimal totalPaid = totalPaidByCustomer.getOrDefault(nearestCustomerId, BigDecimal.ZERO);
        BigDecimal pendingAmount = totalAmount.subtract(totalPaid);
        
        BigDecimal lastPaidAmount = customerPayments.parallelStream()
            .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
            .sorted(Comparator.comparing(Payment::getTimestamp).reversed())
            .findFirst()
            .map(Payment::getAmount)
            .orElse(BigDecimal.ZERO);
        
        CompletableFuture<String> productNameFuture = CompletableFuture.supplyAsync(() -> {
            if (!customerSaleItems.isEmpty()) {
                SaleItem mostRecentSaleItem = customerSaleItems.stream()
                    .sorted(Comparator.comparing(SaleItem::getCreatedAt).reversed())
                    .findFirst()
                    .orElse(null);
                
                if (mostRecentSaleItem != null && mostRecentSaleItem.getProductId() != null) {
                    Optional<Product> product = productRepository.findById(mostRecentSaleItem.getProductId());
                    if (product.isPresent()) {
                        return product.get().getName();
                    }
                }
            }
            return null;
        }, virtualThreadExecutor);
        
        Customer nearestCustomer = customerFuture.join();
        String productName = productNameFuture.join();
        
        return new NearestCustomerResponse(
            nearestCustomer.getId(),
            nearestCustomer.getName(),
            nearestCustomer.getPhone(),
            nearestLocation.getVillage(),
            productName,
            totalPaid,
            pendingAmount,
            lastPaidAmount,
            BigDecimal.valueOf(distanceKm).setScale(2, java.math.RoundingMode.HALF_UP)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerBasicSummaryResponse getCustomerBasicSummary(UUID ownerId, UUID customerId) {
        validateOwnerAccess(ownerId);
        Customer customer = getOwnerCustomer(ownerId, customerId);
        return new CustomerBasicSummaryResponse(
            customer.getId(),
            customer.getName(),
            customer.getPhone(),
            customer.getImageUrl()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSalesSummaryResponse getCustomerSalesSummary(UUID ownerId, UUID customerId) {
        validateOwnerAccess(ownerId);
        getOwnerCustomer(ownerId, customerId);
        List<SaleItem> saleItems = saleItemRepository.findByCustomerIdAndOwnerId(customerId, ownerId);
        long totalSales = saleItems.size();
        BigDecimal totalAmount = saleItems.stream()
            .map(item -> item.getPrice().multiply(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CustomerSalesSummaryResponse(customerId, totalSales, totalAmount);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPaymentsSummaryResponse getCustomerPaymentsSummary(UUID ownerId, UUID customerId) {
        validateOwnerAccess(ownerId);
        getOwnerCustomer(ownerId, customerId);
        List<Payment> payments = paymentRepository.findByCustomerIdAndOwnerId(customerId, ownerId).stream()
            .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
            .toList();
        BigDecimal totalPaid = payments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SaleItem> saleItems = saleItemRepository.findByCustomerIdAndOwnerId(customerId, ownerId);
        BigDecimal totalAmount = saleItems.stream()
            .map(item -> item.getPrice().multiply(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dueAmount = totalAmount.subtract(totalPaid);
        return new CustomerPaymentsSummaryResponse(customerId, (long) payments.size(), totalPaid, totalAmount, dueAmount);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerNotesSummaryResponse getCustomerNotesSummary(UUID ownerId, UUID customerId) {
        validateOwnerAccess(ownerId);
        getOwnerCustomer(ownerId, customerId);
        long totalNotes = noteRepository.findByCustomerIdAndOwnerId(customerId, ownerId).size();
        return new CustomerNotesSummaryResponse(customerId, totalNotes);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerRemindersSummaryResponse getCustomerRemindersSummary(UUID ownerId, UUID customerId) {
        validateOwnerAccess(ownerId);
        getOwnerCustomer(ownerId, customerId);
        List<Reminder> reminders = reminderRepository.findByCustomerIdAndOwnerId(customerId, ownerId);
        long pendingReminders = reminders.stream()
            .filter(r -> r.getStatus() == ReminderStatus.PENDING)
            .count();
        return new CustomerRemindersSummaryResponse(customerId, (long) reminders.size(), pendingReminders);
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_KM = 6371;
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    private static class LocationDetailsWithDistance {
        private final LocationDetails location;
        private final double distance;
        
        public LocationDetailsWithDistance(LocationDetails location, double distance) {
            this.location = location;
            this.distance = distance;
        }
        
        public LocationDetails getLocation() {
            return location;
        }
        
        public double getDistance() {
            return distance;
        }
    }

    private Customer updateCustomerInternal(Customer customer, CustomerUpdateRequest request) {
        UUID ownerId = customer.getOwnerId();
        
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            customer.setName(request.getName());
        }
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String normalizedPhone = PhoneUtil.normalizePhone(request.getPhone());
            customer.setPhone(PhoneUtil.extractPhoneWithoutCountryCode(normalizedPhone));
            customer.setPhoneNormalized(normalizedPhone);
        }
        
        Customer updatedCustomer = customerRepository.save(customer);
        
        if (ownerId != null) {
            invalidateAllRelatedCachesOnCustomerChange(ownerId);
        }
        
        return updatedCustomer;
    }

    private void validateOwnerAccess(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        if (ownerId == null || !ownerId.equals(currentOwnerId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only access your own customers");
        }
    }

    private Customer getOwnerCustomer(UUID ownerId, UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        if (customer.getOwnerId() == null || !customer.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this customer");
        }
        return customer;
    }

    @Transactional(readOnly = true)
    public CustomerSummaryResponse getMySummary() {
        UUID customerId = SecurityUtil.getCurrentUserId();
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        
        UUID ownerId = customer.getOwnerId();
        if (ownerId == null) {
            return new CustomerSummaryResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getImageUrl(),
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            );
        }
        
        List<SaleItem> saleItems = saleItemRepository.findByCustomerIdAndOwnerId(customerId, ownerId);
        long totalSales = saleItems.size();
        BigDecimal totalAmount = saleItems.stream()
            .map(item -> item.getPrice().multiply(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<Payment> payments = paymentRepository.findByCustomerIdAndOwnerId(customerId, ownerId).stream()
            .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
            .toList();
        BigDecimal totalPaid = payments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalDue = totalAmount.subtract(totalPaid);
        
        return new CustomerSummaryResponse(
            customer.getId(),
            customer.getName(),
            customer.getPhone(),
            customer.getImageUrl(),
            totalSales,
            totalAmount,
            totalPaid,
            totalDue
        );
    }
}

