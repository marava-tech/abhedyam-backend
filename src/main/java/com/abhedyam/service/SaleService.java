package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.PaymentCreateRequest;
import com.abhedyam.dto.SaleCreateRequest;
import com.abhedyam.dto.SaleDetailResponse;
import com.abhedyam.dto.SaleItemRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Inventory;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.model.Product;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.ISaleService;
import com.abhedyam.service.interfaces.IStockService;
import com.abhedyam.util.PhoneUtil;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleService implements ISaleService {

    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final LocationDetailsRepository locationDetailsRepository;
    private final IStockService stockService;
    private final RedisTemplate<String, String> redisTemplate;
    private final com.abhedyam.service.interfaces.IAuditService auditService;
    private final CustomerService customerService;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final StatsService statsService;
    private final LocationDetailsService locationDetailsService;
    private final InventoryService inventoryService;

    private static final String IDEMPOTENCY_PREFIX = "sale:idempotency:";
    private static final int IDEMPOTENCY_TTL_MINUTES = 5;
    private static final String WALK_IN_NAME = "Walk-in";
    private static final Set<String> SALE_INPUT_ERRORS = Set.of(
            ErrorCodes.INVALID_QUANTITY, ErrorCodes.PAYMENT_EXCEEDS_DUE,
            ErrorCodes.INVALID_AMOUNT, ErrorCodes.WALK_IN_MUST_BE_PAID);

    @Override
    @Transactional
    public SaleDetailResponse createSale(SaleCreateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        String transactionId = UUID.randomUUID().toString();

        try {
            String clientRedisKey = clientIdempotencyRedisKey(ownerId, request);
            if (clientRedisKey != null) {
                SaleDetailResponse existing = findExistingSale(clientRedisKey);
                if (existing != null) {
                    return existing;
                }
            }

            Customer customer = resolveCustomer(request, ownerId, transactionId);

            List<UUID> productIds = new ArrayList<>();
            List<Product> products = new ArrayList<>();
            List<SaleItemRequest> processedItems = new ArrayList<>();

            for (SaleItemRequest itemRequest : request.getItems()) {
                Product product = resolveProduct(itemRequest, ownerId, transactionId);
                productIds.add(product.getId());
                products.add(product);
                processedItems.add(itemRequest);
            }

            String redisKey = clientRedisKey;
            if (redisKey == null) {
                redisKey = IDEMPOTENCY_PREFIX + generateIdempotencyKey(customer.getId(), productIds);
                SaleDetailResponse existing = findExistingSale(redisKey);
                if (existing != null) {
                    return existing;
                }
            }

            List<SaleItem> saleItems = new ArrayList<>();
            List<ActivityText.Line> activityLines = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (int i = 0; i < processedItems.size(); i++) {
                SaleItemRequest itemRequest = processedItems.get(i);
                Product product = products.get(i);
                UUID productId = productIds.get(i);

                BigDecimal quantity = SalePricing.quantityOf(itemRequest.getQuantity());
                BigDecimal itemPrice = resolveItemPrice(itemRequest, product);

                BigDecimal itemTotal = SalePricing.lineTotal(itemPrice, quantity);
                totalAmount = totalAmount.add(itemTotal);

                SaleItem saleItem = createSaleItem(customer.getId(), ownerId, productId, itemPrice,
                        quantity, itemTotal, transactionId, request.getDueDate());

                SaleItem savedItem = saleItemRepository.save(saleItem);
                saleItems.add(savedItem);
                activityLines.add(new ActivityText.Line(product.getName(), quantity));

                try {
                    stockService.recordSaleOut(productId, quantity, savedItem.getId(),
                            "Sale transaction: " + transactionId);
                } catch (Exception e) {
                    log.error("Stock update failed for product {} in transaction {}, continuing sale: {}",
                            productId, transactionId, e.getMessage());
                }
            }

            boolean walkIn = Boolean.TRUE.equals(request.getWalkIn()) && request.getCustomerId() == null;
            SalePricing.checkPaid(totalAmount, request.getPaidAmount(), walkIn);
            if (request.getPaidAmount() != null && request.getPaidAmount().signum() > 0) {
                recordPaidAtSale(saleItems, request.getPaidAmount(), request.getPaymentMedium());
                saleItems = saleItemRepository.findByTransactionId(transactionId);
            }

            try {
                redisTemplate.opsForValue().set(redisKey, transactionId, IDEMPOTENCY_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Redis cache failed for idempotency key, sale still created: {}", e.getMessage());
            }

            log.info("Sale created successfully: Transaction ID {}, Total Amount: {}, Customer: {}, Items: {}",
                    transactionId, totalAmount, customer.getName(), saleItems.size());

            invalidateOwnerCachesOnSaleCreation(ownerId);

            UUID firstSaleItemId = saleItems.isEmpty() ? null : saleItems.get(0).getId();
            try {
                auditService.logSaleCreation(firstSaleItemId != null ? firstSaleItemId : UUID.randomUUID(),
                        ownerId, customer.getId(), customer.getName(), totalAmount, transactionId,
                        ActivityText.itemsSummary(activityLines), request.getPaidAmount());
            } catch (Exception e) {
                log.warn("Audit logging failed, sale still created: {}", e.getMessage());
            }

            return buildSaleDetailResponse(transactionId, customer, saleItems, totalAmount);

        } catch (BusinessException e) {
            if (SALE_INPUT_ERRORS.contains(e.getErrorCode())) {
                throw e;
            }
            log.error("Business error in sale creation for transaction {}: {}", transactionId, e.getMessage(), e);
            throw new BusinessException("SALE_CREATION_FAILED",
                    "Unable to create sale. Please try again");
        } catch (Exception e) {
            log.error("Critical error in sale creation for transaction {}: {}", transactionId, e.getMessage(), e);
            throw new BusinessException("SALE_CREATION_FAILED",
                    "Unable to create sale. Please try again");
        }
    }

    private Customer resolveCustomer(SaleCreateRequest request, UUID ownerId, String transactionId) {
        if (request.getCustomerId() == null && Boolean.TRUE.equals(request.getWalkIn())) {
            return customerRepository.findFirstByOwnerIdAndNameIgnoreCase(ownerId, WALK_IN_NAME)
                    .orElseGet(() -> {
                        request.setCustomerName(WALK_IN_NAME);
                        request.setCustomerPhone(null);
                        return findOrCreateCustomer(request, ownerId);
                    });
        }
        if (request.getCustomerId() != null) {
            return customerRepository.findById(request.getCustomerId())
                    .map(customer -> {
                        if (customer.getOwnerId() != null && !customer.getOwnerId().equals(ownerId)) {
                            log.warn("Customer access denied for transaction {}, checking for existing customer",
                                    transactionId);
                            return findOrCreateCustomer(request, ownerId);
                        }
                        if (customer.getIsActive() == null || !customer.getIsActive()) {
                            log.warn("Customer {} is inactive for transaction {}, reactivating", customer.getId(),
                                    transactionId);
                            customer.setIsActive(true);
                            return customerRepository.save(customer);
                        }
                        log.info("Reusing existing customer: {} ({})", customer.getName(), customer.getId());
                        return customer;
                    })
                    .orElseGet(() -> {
                        log.info("Customer {} not found for transaction {}, checking for existing customer",
                                request.getCustomerId(), transactionId);
                        return findOrCreateCustomer(request, ownerId);
                    });
        } else {
            String customerName = request.getCustomerName();
            if (customerName == null || customerName.trim().isEmpty()) {
                customerName = "Customer-" + System.currentTimeMillis();
                log.warn("Customer name missing for transaction {}, using default: {}", transactionId, customerName);
            }
            request.setCustomerName(customerName);
            return findOrCreateCustomer(request, ownerId);
        }
    }

    private Customer findOrCreateCustomer(SaleCreateRequest request, UUID ownerId) {
        if (request.getCustomerPhone() != null && !request.getCustomerPhone().trim().isEmpty()) {
            try {
                String normalizedPhone = PhoneUtil.normalizePhone(request.getCustomerPhone());
                Optional<Customer> existingCustomerOpt = customerRepository.findByPhoneNormalized(normalizedPhone);

                if (existingCustomerOpt.isPresent()) {
                    Customer existingCustomer = existingCustomerOpt.get();
                    if (existingCustomer.getOwnerId() == null) {
                        customerRepository.delete(existingCustomer);
                        customerRepository.flush();
                        log.info("Deleted existing customer with no owner (phone: {}) before creating new customer",
                                normalizedPhone);
                    } else if (!existingCustomer.getOwnerId().equals(ownerId)) {
                        throw new BusinessException("CUSTOMER_EXISTS",
                                "Customer with this phone number already exists and is associated with another owner");
                    } else {
                        log.info("Customer already exists with this owner (phone: {}), returning existing customer",
                                normalizedPhone);
                        return existingCustomer;
                    }
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Phone normalization or existing customer check failed, continuing without phone: {}",
                        e.getMessage());
            }
        }

        Customer customer = new Customer();
        customer.setName(request.getCustomerName().trim());
        customer.setType(UserType.CUSTOMER);
        customer.setOwnerId(ownerId);
        customer.setIsActive(true);

        if (request.getCustomerPhone() != null && !request.getCustomerPhone().trim().isEmpty()) {
            try {
                String normalizedPhone = PhoneUtil.normalizePhone(request.getCustomerPhone());
                customer.setPhone(PhoneUtil.extractPhoneWithoutCountryCode(normalizedPhone));
                customer.setPhoneNormalized(normalizedPhone);
            } catch (Exception e) {
                log.warn("Phone normalization failed, continuing without phone: {}", e.getMessage());
            }
        }

        customer = customerRepository.save(customer);

        try {
            customerService.invalidateOwnerCaches(ownerId);
        } catch (Exception e) {
            log.warn("Customer cache invalidate failed on sale create for owner {}: {}", ownerId, e.getMessage());
        }

        try {
            String villageName = (request.getCustomerVillage() != null
                    && !request.getCustomerVillage().trim().isEmpty())
                            ? request.getCustomerVillage().trim()
                            : "No Village";

            LocationDetails locationDetails = new LocationDetails();
            locationDetails.setUserId(customer.getId());
            locationDetails.setVillage(villageName);
            locationDetails.setLatitude(BigDecimal.ZERO);
            locationDetails.setLongitude(BigDecimal.ZERO);
            locationDetailsRepository.save(locationDetails);
        } catch (Exception e) {
            log.warn("Location details save failed for customer {}: {}", customer.getId(), e.getMessage());
        }

        log.info("Customer created on the fly: {} ({})", customer.getName(),
                customer.getPhone() != null ? customer.getPhone() : "no phone");
        return customer;
    }

    private Product resolveProduct(SaleItemRequest itemRequest, UUID ownerId, String transactionId) {
        if (itemRequest.getProductId() != null) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product could not be found"));

            if (!product.getOwnerId().equals(ownerId)) {
                throw new BusinessException(ErrorCodes.UNAUTHORIZED,
                        "You don't have permission to access this product");
            }

            if (product.getIsActive() == null || !product.getIsActive()) {
                log.warn("Product {} is inactive for transaction {}, reactivating", product.getId(), transactionId);
                product.setIsActive(true);
                product = productRepository.save(product);
            }

            if (product.getPrice() == null && itemRequest.getPrice() != null) {
                log.warn("Product {} has no price for transaction {}, using provided price",
                        product.getId(), transactionId);
                product.setPrice(itemRequest.getPrice());
                product = productRepository.save(product);
            }

            log.info("Using product by ID: {} ({})", product.getName(), product.getId());
            return product;
        } else if (itemRequest.getProductName() != null && !itemRequest.getProductName().trim().isEmpty()) {
            return findOrCreateProduct(itemRequest, ownerId);
        } else {
            return createNewProduct(itemRequest, ownerId);
        }
    }

    private Product findOrCreateProduct(SaleItemRequest itemRequest, UUID ownerId) {
        String productName = itemRequest.getProductName().trim();
        BigDecimal price = itemRequest.getPrice();

        // Find by name only, ignoring price to reuse existing product
        List<Product> existingProducts = productRepository.findByOwnerIdAndName(ownerId, productName);

        if (!existingProducts.isEmpty()) {
            // Prefer active product
            Product product = existingProducts.stream()
                    .filter(p -> p.getIsActive() != null && p.getIsActive())
                    .findFirst()
                    .orElse(existingProducts.get(0));

            if (product.getIsActive() == null || !product.getIsActive()) {
                log.info("Found inactive product with same name, reactivating: {} ({})",
                        product.getName(), product.getId());
                product.setIsActive(true);
                product = productRepository.save(product);
            } else {
                log.info("Reusing existing product with same name: {} ({})",
                        product.getName(), product.getId());
            }
            return product;
        }

        log.info("No existing product found with name '{}', creating new product", productName);
        return createNewProduct(itemRequest, ownerId, productName, price);
    }

    private Product createNewProduct(SaleItemRequest itemRequest, UUID ownerId) {
        String productName = itemRequest.getProductName();
        if (productName == null || productName.trim().isEmpty()) {
            productName = "Product-" + System.currentTimeMillis();
            log.warn("Product name missing, using default: {}", productName);
        } else {
            productName = productName.trim();
        }

        BigDecimal price = itemRequest.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            price = BigDecimal.ZERO;
            log.warn("Product price missing or invalid, using default: {}", price);
        }

        return createNewProduct(itemRequest, ownerId, productName, price);
    }

    private Product createNewProduct(SaleItemRequest itemRequest, UUID ownerId, String productName, BigDecimal price) {
        String productCode = "PROD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int attempts = 0;
        while (productRepository.findByOwnerIdAndCode(ownerId, productCode).isPresent() && attempts < 10) {
            productCode = "PROD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            attempts++;
        }

        Product product = new Product();
        product.setCode(productCode);
        product.setName(productName);
        product.setPrice(price);
        product.setOwnerId(ownerId);
        product.setIsActive(true);
        product = productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProductId(product.getId());
        inventory.setOwnerId(ownerId);
        inventory.setStock(BigDecimal.ZERO);
        inventoryRepository.save(inventory);

        try {
            auditService.logProductCreation(product.getId(), ownerId, product.getName(), product.getCode());
        } catch (Exception e) {
            log.warn("Audit logging failed for product creation: {}", e.getMessage());
        }

        log.info("Product created on the fly: {} ({}) - Inventory created with stock 0", product.getName(),
                product.getCode());
        return product;
    }

    private BigDecimal resolveItemPrice(SaleItemRequest itemRequest, Product product) {
        // Always prioritize the price from the request (sold price) if available
        if (itemRequest.getPrice() != null && itemRequest.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            return itemRequest.getPrice();
        }

        // Fallback to product's base price
        BigDecimal price = product.getPrice();
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            return price;
        }

        log.warn("No valid price found for product {}, using default: 0", product.getId());
        return BigDecimal.ZERO;
    }

    private SaleItem createSaleItem(UUID customerId, UUID ownerId, UUID productId, BigDecimal price,
            BigDecimal quantity, BigDecimal itemTotal, String transactionId, Instant dueDate) {
        SaleItem saleItem = new SaleItem();
        saleItem.setProductId(productId);
        saleItem.setCustomerId(customerId);
        saleItem.setOwnerId(ownerId);
        saleItem.setPrice(price);
        saleItem.setQuantity(quantity);
        saleItem.setRemainingAmount(itemTotal);
        saleItem.setStatus(com.abhedyam.model.enums.SaleItemStatus.NOT_PAID);
        saleItem.setTransactionId(transactionId);
        saleItem.setDueDate(dueDate);
        return saleItem;
    }

    private String clientIdempotencyRedisKey(UUID ownerId, SaleCreateRequest request) {
        String key = request.getIdempotencyKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        return IDEMPOTENCY_PREFIX + "client:" + ownerId + ":" + key.trim();
    }

    private SaleDetailResponse findExistingSale(String redisKey) {
        try {
            String existingTransactionId = redisTemplate.opsForValue().get(redisKey);
            if (existingTransactionId != null) {
                log.warn("Duplicate sale request detected, returning existing transaction: {}", existingTransactionId);
                return getSaleByTransactionId(existingTransactionId);
            }
        } catch (Exception e) {
            log.warn("Redis check failed for idempotency, proceeding with sale creation: {}", e.getMessage());
        }
        return null;
    }

    private void recordPaidAtSale(List<SaleItem> saleItems, BigDecimal paid, PaymentMedium medium) {
        List<PaymentAllocator.Open> open = saleItems.stream()
                .map(si -> new PaymentAllocator.Open(si.getId(), si.getRemainingAmount()))
                .toList();
        for (PaymentAllocator.Piece piece : PaymentAllocator.allocate(open, paid)) {
            PaymentCreateRequest one = new PaymentCreateRequest();
            one.setSaleItemId(piece.saleItemId());
            one.setAmount(piece.amount());
            one.setMedium(medium != null ? medium : PaymentMedium.CASH);
            one.setStatus(PaymentStatus.SUCCESS);
            paymentService.createManualPayment(one);
        }
    }

    private String generateIdempotencyKey(UUID customerId, List<UUID> productIds) {
        try {
            List<String> sortedProductIds = productIds.stream()
                    .map(UUID::toString)
                    .sorted()
                    .collect(Collectors.toList());

            String combined = customerId.toString() + ":" + String.join(",", sortedProductIds);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(combined.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString().substring(0, 32);
        } catch (Exception e) {
            log.error("Error generating idempotency key", e);
            return customerId.toString() + "-" + productIds.stream()
                    .map(UUID::toString)
                    .sorted()
                    .collect(Collectors.joining("-"));
        }
    }

    @Override
    public SaleDetailResponse getSaleByTransactionId(String transactionId) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        List<SaleItem> saleItems = saleItemRepository.findByTransactionId(transactionId);

        if (saleItems.isEmpty()) {
            throw new ResourceNotFoundException("Sale could not be found");
        }

        SaleItem firstItem = saleItems.get(0);
        if (!firstItem.getOwnerId().equals(ownerId)) {
            throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this sale");
        }

        Customer customer = customerRepository.findById(firstItem.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));

        BigDecimal totalAmount = saleItems.stream()
                .map(item -> item.getPrice().multiply(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return buildSaleDetailResponse(transactionId, customer, saleItems, totalAmount);
    }

    @Override
    @Transactional
    public SaleDetailResponse cancelSale(String transactionId) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        List<SaleItem> saleItems = saleItemRepository.findByTransactionId(transactionId);

        if (saleItems.isEmpty()) {
            throw new ResourceNotFoundException("Sale could not be found");
        }

        SaleItem firstItem = saleItems.get(0);
        if (!firstItem.getOwnerId().equals(ownerId)) {
            throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this sale");
        }

        for (SaleItem saleItem : saleItems) {
            productRepository.findById(saleItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product could not be found"));

            BigDecimal quantity = saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE;

            com.abhedyam.dto.StockAdjustmentRequest adjustmentRequest = new com.abhedyam.dto.StockAdjustmentRequest();
            adjustmentRequest.setProductId(saleItem.getProductId());
            adjustmentRequest.setChangeQty(quantity);
            adjustmentRequest.setNote("Sale cancellation - Transaction: " + transactionId);

            stockService.recordManualAdjustment(adjustmentRequest);

            saleItem.setDeletedAt(Instant.now());
            saleItem.setIsActive(false);
            saleItemRepository.save(saleItem);
        }

        log.info("Sale cancelled: Transaction ID {}", transactionId);

        Customer customer = customerRepository.findById(firstItem.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));

        BigDecimal totalAmount = saleItems.stream()
                .map(item -> item.getPrice().multiply(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        UUID firstSaleItemId = saleItems.isEmpty() ? null : saleItems.get(0).getId();
        auditService.logSaleCancellation(firstSaleItemId != null ? firstSaleItemId : UUID.randomUUID(),
                ownerId, customer.getId(), customer.getName(), totalAmount, transactionId);

        return buildSaleDetailResponse(transactionId, customer, saleItems, totalAmount);
    }

    private SaleDetailResponse buildSaleDetailResponse(String transactionId, Customer customer,
            List<SaleItem> saleItems, BigDecimal totalAmount) {
        BigDecimal totalDue = saleItems.stream()
                .map(item -> item.getRemainingAmount() != null
                        ? item.getRemainingAmount()
                        : item.getPrice().multiply(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = totalAmount.subtract(totalDue);

        SaleItem firstItem = saleItems.isEmpty() ? null : saleItems.get(0);

        return new SaleDetailResponse(
                transactionId,
                customer.getId(),
                customer.getName(),
                firstItem != null ? firstItem.getOwnerId() : null,
                saleItems,
                totalAmount,
                firstItem != null ? firstItem.getCreatedAt() : Instant.now(),
                firstItem != null ? firstItem.getDueDate() : null,
                totalPaid,
                totalDue);
    }

    private void invalidateOwnerCachesOnSaleCreation(UUID ownerId) {
        try {
            customerService.invalidateOwnerCaches(ownerId);
            productService.invalidateOwnerCaches(ownerId);
            paymentService.invalidateOwnerCaches(ownerId);
            statsService.invalidateOwnerCaches(ownerId);
            locationDetailsService.invalidateOwnerCaches(ownerId);
            inventoryService.invalidateOwnerCaches(ownerId);
            log.debug("Completed cache invalidation for owner {} on sale creation", ownerId);
        } catch (Exception e) {
            log.warn("Error during cache invalidation on sale creation for owner {}: {}", ownerId, e.getMessage());
        }
    }
}
