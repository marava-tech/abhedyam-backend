package com.abhedyam.service;

import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.PaymentCreateRequest;
import com.abhedyam.dto.PaymentResponse;
import com.abhedyam.dto.PaymentStatusUpdateRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Payment;
import com.abhedyam.model.Product;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.model.enums.SaleItemStatus;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.IAuditService;
import com.abhedyam.service.interfaces.ICustomerService;
import com.abhedyam.service.interfaces.IPaymentService;
import com.abhedyam.util.SecurityUtil;
import com.abhedyam.constants.CacheKeys;
import com.abhedyam.constants.ErrorCodes;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService implements IPaymentService {
    
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SaleItemRepository saleItemRepository;
    private final IAuditService auditService;
    private final ICustomerService customerService;
    private final CustomerService customerServiceConcrete;
    private final RedisTemplate<String, String> redisTemplate;
    private final StatsService statsService;
    private final com.abhedyam.repository.ShopMemberRepository memberRepository;

    @Override
    public Payment create(Payment payment) {
        return paymentRepository.save(payment);
    }
    
    @Override
    @Transactional
    public PaymentResponse createManualPayment(PaymentCreateRequest request) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodes.INVALID_AMOUNT, "Payment amount must be greater than zero");
        }
        
        SaleItem saleItem = saleItemRepository.findById(request.getSaleItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Sale item could not be found"));
        
        UUID saleItemOwnerId = saleItem.getOwnerId();
        
        if (saleItemOwnerId == null || !saleItemOwnerId.equals(currentUserId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this sale item");
        }
        UUID paymentOwnerId = currentUserId;
        
        BigDecimal remainingAmount = saleItem.getRemainingAmount();
        if (remainingAmount == null) {
            BigDecimal totalAmount = saleItem.getPrice().multiply(
                saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE
            );
            remainingAmount = totalAmount;
        }
        
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodes.NO_DUE_AMOUNT, "No payment is due for this sale");
        }
        
        if (request.getAmount().compareTo(remainingAmount) > 0) {
            throw new BusinessException(ErrorCodes.PAYMENT_EXCEEDS_DUE, 
                String.format("Payment amount (₹%s) cannot exceed the due amount (₹%s). You can pay maximum ₹%s.", 
                    request.getAmount(), remainingAmount, remainingAmount));
        }
        
        Customer customer = customerRepository.findById(saleItem.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        
        Product product = productRepository.findById(saleItem.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product could not be found"));
        
        Payment payment = new Payment();
        payment.setCustomerId(saleItem.getCustomerId());
        payment.setOwnerId(paymentOwnerId);
        payment.setSaleItemId(request.getSaleItemId());
        payment.setAmount(request.getAmount());
        payment.setMedium(request.getMedium());
        payment.setTimestamp(Instant.now());
        payment.setReference(request.getReference());
        payment.setDueId(request.getDueId());
        payment.setStatus(request.getStatus() != null ? request.getStatus() : PaymentStatus.SUCCESS);
        payment.setWriteOff(Boolean.TRUE.equals(request.getWriteOff()) ? Boolean.TRUE : null);
        
        Payment savedPayment = paymentRepository.save(payment);
        
        invalidateAllRelatedCachesOnPaymentChange(paymentOwnerId, savedPayment.getCustomerId());
        
        if (savedPayment.getStatus() == PaymentStatus.SUCCESS) {
            updateSaleItemBalance(saleItem, savedPayment.getAmount(), true);
            auditService.logPaymentSuccess(
                savedPayment.getId(),
                paymentOwnerId,
                customer.getId(),
                customer.getName(),
                request.getSaleItemId(),
                product.getName(),
                savedPayment.getAmount(),
                savedPayment.getReference(),
                savedPayment.getMedium() != null ? savedPayment.getMedium().name() : null
            );
        } else {
            auditService.logPaymentCreation(
                savedPayment.getId(),
                paymentOwnerId,
                customer.getId(),
                customer.getName(),
                request.getSaleItemId(),
                product.getName(),
                savedPayment.getAmount(),
                savedPayment.getReference(),
                savedPayment.getStatus().toString(),
                savedPayment.getMedium().toString()
            );
        }
        
        return new PaymentResponse(
                savedPayment.getId(),
                savedPayment.getCustomerId(),
                customer.getName(),
                savedPayment.getOwnerId(),
                savedPayment.getSaleItemId(),
                product.getName(),
                savedPayment.getAmount(),
                savedPayment.getMedium(),
                savedPayment.getTimestamp(),
                savedPayment.getReference(),
                savedPayment.getStatus(),
                savedPayment.getCreatedAt(),
                savedPayment.getUpdatedAt(),
                Boolean.TRUE.equals(savedPayment.getWriteOff())
        );
    }

    @Override
    @Transactional
    public com.abhedyam.dto.CollectPaymentResponse collectFromCustomer(UUID ownerId, com.abhedyam.dto.CollectPaymentRequest request) {
        String key = request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
                ? null : "payment:idempotency:client:" + ownerId + ":" + request.getIdempotencyKey().trim();
        if (key != null && !claimOnce(key)) {
            log.warn("Duplicate payment request ignored: {}", key);
            return new com.abhedyam.dto.CollectPaymentResponse(request.getCustomerId(), request.getAmount(), List.of());
        }
        try {
            return collect(ownerId, request, false);
        } catch (RuntimeException e) {
            if (key != null) release(key); // not recorded, so a retry must go through
            throw e;
        }
    }

    /** True the first time a key is seen in 10 minutes. Redis down = allow (never block taking money). */
    private boolean claimOnce(String key) {
        try {
            return !Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", java.time.Duration.ofMinutes(10)));
        } catch (Exception e) {
            log.warn("Redis idempotency check failed, recording payment: {}", e.getMessage());
            return true;
        }
    }

    private void release(String key) {
        try { redisTemplate.delete(key); } catch (Exception ignored) { }
    }

    /** Old dues that will not come back: closes the whole open balance (oldest sale first), no money in, no notice. */
    @Override
    @Transactional
    public com.abhedyam.dto.CollectPaymentResponse writeOffCustomer(UUID ownerId, UUID customerId) {
        com.abhedyam.dto.CollectPaymentRequest request = new com.abhedyam.dto.CollectPaymentRequest();
        request.setCustomerId(customerId);
        request.setMedium(PaymentMedium.OFFLINE); // old apps know OFFLINE; a new medium would make them throw
        request.setReference("Written off");
        return collect(ownerId, request, true);
    }

    /** Undo a write-off: puts each amount back on its sale and removes the written-off rows. Returns how many. */
    @Override
    @Transactional
    public int undoWriteOff(UUID ownerId, UUID customerId) {
        validateOwnerAccess(ownerId);
        List<Payment> rows = paymentRepository.findByCustomerIdAndOwnerId(customerId, ownerId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getWriteOff()))
                .toList();
        for (Payment p : rows) {
            if (p.getSaleItemId() != null) {
                saleItemRepository.findById(p.getSaleItemId()).ifPresent(si -> updateSaleItemBalance(si, p.getAmount(), false));
            }
        }
        paymentRepository.deleteAll(rows);
        invalidateAllRelatedCachesOnPaymentChange(ownerId, customerId);
        return rows.size();
    }

    /** [writeOff]: the amount is the whole open balance and the rows are marked written off. */
    private com.abhedyam.dto.CollectPaymentResponse collect(UUID ownerId, com.abhedyam.dto.CollectPaymentRequest request, boolean writeOff) {
        validateOwnerAccess(ownerId);

        if (!writeOff && (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException(ErrorCodes.INVALID_AMOUNT, "Payment amount must be greater than zero");
        }

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        if (!ownerId.equals(customer.getOwnerId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this customer");
        }

        List<SaleItem> items = saleItemRepository.findByCustomerIdAndOwnerIdOrderByCreatedAtAsc(customer.getId(), ownerId);
        List<PaymentAllocator.Open> open = items.stream()
                .map(si -> new PaymentAllocator.Open(si.getId(), si.getRemainingAmount() != null
                        ? si.getRemainingAmount()
                        : si.getPrice().multiply(si.getQuantity() != null ? si.getQuantity() : BigDecimal.ONE)))
                .toList();

        BigDecimal totalDue = PaymentAllocator.total(open);
        if (totalDue.signum() <= 0) {
            throw new BusinessException(ErrorCodes.NO_DUE_AMOUNT, "No payment is due for this customer");
        }
        if (writeOff) request.setAmount(totalDue);
        if (request.getAmount().compareTo(totalDue) > 0) {
            throw new BusinessException(ErrorCodes.PAYMENT_EXCEEDS_DUE,
                    String.format("Payment amount (₹%s) cannot exceed the total due (₹%s).", request.getAmount(), totalDue));
        }

        PaymentMedium medium = request.getMedium() != null ? request.getMedium() : PaymentMedium.CASH;
        List<PaymentResponse> payments = new java.util.ArrayList<>();
        for (PaymentAllocator.Piece piece : PaymentAllocator.allocate(open, request.getAmount())) {
            PaymentCreateRequest one = new PaymentCreateRequest();
            one.setSaleItemId(piece.saleItemId());
            one.setAmount(piece.amount());
            one.setMedium(medium);
            one.setReference(request.getReference());
            one.setStatus(PaymentStatus.SUCCESS);
            one.setDueId(request.getDueId());
            one.setWriteOff(writeOff ? Boolean.TRUE : null);
            payments.add(createManualPayment(one));
        }
        return new com.abhedyam.dto.CollectPaymentResponse(customer.getId(), request.getAmount(), payments);
    }

    @Transactional
    public PaymentResponse createImportedPayment(UUID ownerId, PaymentCreateRequest request, Instant timestamp) {
        validateOwnerAccess(ownerId);

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodes.INVALID_AMOUNT, "Payment amount must be greater than zero");
        }

        if (request.getMedium() == null) {
            request.setMedium(PaymentMedium.CASH);
        }

        SaleItem saleItem = saleItemRepository.findById(request.getSaleItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Sale item could not be found"));

        if (!ownerId.equals(saleItem.getOwnerId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this sale item");
        }

        BigDecimal remainingAmount = saleItem.getRemainingAmount();
        if (remainingAmount == null) {
            BigDecimal totalAmount = saleItem.getPrice().multiply(
                saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE
            );
            remainingAmount = totalAmount;
        }

        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodes.NO_DUE_AMOUNT, "No payment is due for this sale");
        }

        if (request.getAmount().compareTo(remainingAmount) > 0) {
            throw new BusinessException(ErrorCodes.PAYMENT_EXCEEDS_DUE,
                String.format("Payment amount (₹%s) cannot exceed the due amount (₹%s). You can pay maximum ₹%s.",
                    request.getAmount(), remainingAmount, remainingAmount));
        }

        Customer customer = customerRepository.findById(saleItem.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));

        Product product = productRepository.findById(saleItem.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product could not be found"));

        Payment payment = new Payment();
        payment.setCustomerId(saleItem.getCustomerId());
        payment.setOwnerId(ownerId);
        payment.setSaleItemId(request.getSaleItemId());
        payment.setAmount(request.getAmount());
        payment.setMedium(request.getMedium());
        payment.setTimestamp(timestamp != null ? timestamp : Instant.now());
        payment.setReference(request.getReference());
        payment.setStatus(request.getStatus() != null ? request.getStatus() : PaymentStatus.SUCCESS);

        Payment savedPayment = paymentRepository.save(payment);

        invalidateAllRelatedCachesOnPaymentChange(ownerId, savedPayment.getCustomerId());

        if (savedPayment.getStatus() == PaymentStatus.SUCCESS) {
            updateSaleItemBalance(saleItem, savedPayment.getAmount(), true);
            auditService.logPaymentSuccess(
                savedPayment.getId(),
                ownerId,
                customer.getId(),
                customer.getName(),
                request.getSaleItemId(),
                product.getName(),
                savedPayment.getAmount(),
                savedPayment.getReference(),
                savedPayment.getMedium() != null ? savedPayment.getMedium().name() : null
            );
        } else {
            auditService.logPaymentCreation(
                savedPayment.getId(),
                ownerId,
                customer.getId(),
                customer.getName(),
                request.getSaleItemId(),
                product.getName(),
                savedPayment.getAmount(),
                savedPayment.getReference(),
                savedPayment.getStatus().toString(),
                savedPayment.getMedium().toString()
            );
        }

        return new PaymentResponse(
                savedPayment.getId(),
                savedPayment.getCustomerId(),
                customer.getName(),
                savedPayment.getOwnerId(),
                savedPayment.getSaleItemId(),
                product.getName(),
                savedPayment.getAmount(),
                savedPayment.getMedium(),
                savedPayment.getTimestamp(),
                savedPayment.getReference(),
                savedPayment.getStatus(),
                savedPayment.getCreatedAt(),
                savedPayment.getUpdatedAt(),
                Boolean.TRUE.equals(savedPayment.getWriteOff())
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public Payment getById(UUID id) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment could not be found"));
        
        if (payment.getOwnerId() == null || !payment.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this payment");
        }
        
        return payment;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getOwnerPayments(UUID ownerId, String searchText, Integer page, Integer size, String sortBy, String sortDirection) {
        validateOwnerAccess(ownerId);
        
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        
        String normalizedSearchText = searchText != null && !searchText.trim().isEmpty() ? searchText.trim() : null;
        boolean isNumeric = false;
        BigDecimal amount = null;
        if (normalizedSearchText != null) {
            try {
                amount = new BigDecimal(normalizedSearchText);
                isNumeric = true;
            } catch (NumberFormatException e) {
                isNumeric = false;
            }
        }
        
        Sort sort = Sort.by(
            "DESC".equalsIgnoreCase(sortDirection) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC,
            sortBy != null && !sortBy.trim().isEmpty() ? sortBy : "createdAt"
        );
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Payment> paymentPage = normalizedSearchText == null
            ? paymentRepository.findByOwnerId(ownerId, pageable)
            : paymentRepository.searchPayments(ownerId, normalizedSearchText, isNumeric, amount, pageable);
        
        List<Payment> payments = paymentPage.getContent();
        List<PaymentResponse> responses = mapPaymentResponses(payments);
        
        return new PageResponse<>(
            responses,
            paymentPage.getNumber(),
            paymentPage.getSize(),
            paymentPage.getTotalElements(),
            paymentPage.getTotalPages(),
            paymentPage.hasNext(),
            paymentPage.hasPrevious()
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> filterPayments(String searchText) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        String normalizedSearchText = searchText != null && !searchText.trim().isEmpty() ? searchText.trim() : "";
        
        boolean isNumeric = false;
        BigDecimal amount = null;
        
        if (!normalizedSearchText.isEmpty()) {
            try {
                amount = new BigDecimal(normalizedSearchText);
                isNumeric = true;
            } catch (NumberFormatException e) {
                isNumeric = false;
            }
        }
        
        List<Payment> payments = paymentRepository.filterPayments(
            ownerId,
            normalizedSearchText.isEmpty() ? null : normalizedSearchText,
            isNumeric,
            amount
        );
        
        List<Payment> sortedPayments = payments.stream()
                .sorted((p1, p2) -> {
                    if (p1.getCreatedAt() == null && p2.getCreatedAt() == null) return 0;
                    if (p1.getCreatedAt() == null) return 1;
                    if (p2.getCreatedAt() == null) return -1;
                    return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                })
                .limit(15)
                .toList();
        
        if (sortedPayments.isEmpty()) {
            return List.of();
        }
        
        List<UUID> customerIds = sortedPayments.stream()
            .map(Payment::getCustomerId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        List<Customer> customers = customerIds.isEmpty() ? List.of() : 
            customerRepository.findByIdIn(customerIds);
        java.util.Map<UUID, String> customerNameMap = customers.stream()
            .collect(java.util.stream.Collectors.toMap(
                Customer::getId,
                Customer::getName,
                (v1, v2) -> v1
            ));
        
        List<UUID> saleItemIds = sortedPayments.stream()
            .map(Payment::getSaleItemId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        List<SaleItem> saleItems = saleItemIds.isEmpty() ? List.of() : 
            saleItemRepository.findByIdIn(saleItemIds);
        java.util.Map<UUID, UUID> saleItemProductMap = saleItems.stream()
            .collect(java.util.stream.Collectors.toMap(
                SaleItem::getId,
                SaleItem::getProductId,
                (v1, v2) -> v1
            ));
        
        List<UUID> productIds = saleItems.stream()
            .map(SaleItem::getProductId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        List<Product> products = productIds.isEmpty() ? List.of() : 
            productRepository.findByIdIn(productIds);
        java.util.Map<UUID, String> productNameMap = products.stream()
            .collect(java.util.stream.Collectors.toMap(
                Product::getId,
                Product::getName,
                (v1, v2) -> v1
            ));
        
        // "by Ravi": names of the shop's family members / helpers
        java.util.Map<UUID, String> memberNames = new java.util.HashMap<>();
        if (!sortedPayments.isEmpty()) {
            memberRepository.findByOwnerId(sortedPayments.get(0).getOwnerId()).forEach(m -> memberNames.put(m.getId(), m.getName()));
        }
        return sortedPayments.stream()
                .map(payment -> {
                    String customerName = customerNameMap.getOrDefault(
                        payment.getCustomerId(), "Unknown");
                    
                    String productName = "Unknown";
                    if (payment.getSaleItemId() != null) {
                        UUID productId = saleItemProductMap.get(payment.getSaleItemId());
                        if (productId != null) {
                            productName = productNameMap.getOrDefault(productId, "Unknown");
                        }
                    }
                    
                    PaymentResponse r = new PaymentResponse(
                            payment.getId(),
                            payment.getCustomerId(),
                            customerName,
                            payment.getOwnerId(),
                            payment.getSaleItemId(),
                            productName,
                            payment.getAmount(),
                            payment.getMedium(),
                            payment.getTimestamp(),
                            payment.getReference(),
                            payment.getStatus(),
                            payment.getCreatedAt(),
                            payment.getUpdatedAt(),
                            Boolean.TRUE.equals(payment.getWriteOff())
                    );
                    if (payment.getAddedBy() != null) r.setAddedByName(memberNames.get(payment.getAddedBy()));
                    return r;
                })
                .toList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Payment> getByCustomerId(UUID customerId) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        
        if (customer.getOwnerId() == null || !customer.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this customer's payments");
        }
        
        List<Payment> payments = paymentRepository.findByCustomerIdAndOwnerId(customerId, currentUserId);
        return payments.stream()
            .sorted((p1, p2) -> {
                if (p1.getCreatedAt() == null && p2.getCreatedAt() == null) return 0;
                if (p1.getCreatedAt() == null) return 1;
                if (p2.getCreatedAt() == null) return -1;
                return p2.getCreatedAt().compareTo(p1.getCreatedAt());
            })
            .toList();
    }
    
    @Override
    @Transactional
    public Payment updateStatus(UUID id, PaymentStatusUpdateRequest request) {
        Payment payment = getById(id);
        PaymentStatus oldStatus = payment.getStatus();
        
        if (payment.getSaleItemId() != null) {
            SaleItem saleItem = saleItemRepository.findById(payment.getSaleItemId()).orElse(null);
            if (saleItem != null) {
                if (oldStatus == PaymentStatus.SUCCESS && request.getStatus() != PaymentStatus.SUCCESS) {
                    updateSaleItemBalance(saleItem, payment.getAmount(), false);
                } else if (oldStatus != PaymentStatus.SUCCESS && request.getStatus() == PaymentStatus.SUCCESS) {
                    BigDecimal remainingAmount = saleItem.getRemainingAmount();
                    if (remainingAmount == null) {
                        BigDecimal totalAmount = saleItem.getPrice().multiply(
                            saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE
                        );
                        remainingAmount = totalAmount;
                    }
                    
                    if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(ErrorCodes.NO_DUE_AMOUNT, "No payment is due for this sale");
                    }
                    
                    if (payment.getAmount().compareTo(remainingAmount) > 0) {
                        throw new BusinessException(ErrorCodes.PAYMENT_EXCEEDS_DUE, 
                            String.format("Payment amount (₹%s) cannot exceed the due amount (₹%s). You can pay maximum ₹%s.", 
                                payment.getAmount(), remainingAmount, remainingAmount));
                    }
                    
                    updateSaleItemBalance(saleItem, payment.getAmount(), true);
                }
            }
        }
        
        payment.setStatus(request.getStatus());
        if (request.getReference() != null) {
            payment.setReference(request.getReference());
        }
        Payment savedPayment = paymentRepository.save(payment);
        
        invalidateAllRelatedCachesOnPaymentChange(savedPayment.getOwnerId(), savedPayment.getCustomerId());
        
        if (oldStatus != PaymentStatus.SUCCESS && request.getStatus() == PaymentStatus.SUCCESS) {
            Customer customer = customerRepository.findById(payment.getCustomerId())
                    .orElse(null);
            String customerName = customer != null ? customer.getName() : "Unknown";
            
            String productName = "Unknown";
            if (payment.getSaleItemId() != null) {
                SaleItem saleItem = saleItemRepository.findById(payment.getSaleItemId()).orElse(null);
                if (saleItem != null) {
                    Product product = productRepository.findById(saleItem.getProductId()).orElse(null);
                    if (product != null) {
                        productName = product.getName();
                    }
                }
            }
            
            auditService.logPaymentSuccess(
                savedPayment.getId(),
                payment.getOwnerId(),
                payment.getCustomerId(),
                customerName,
                payment.getSaleItemId(),
                productName,
                payment.getAmount(),
                payment.getReference(),
                payment.getMedium() != null ? payment.getMedium().name() : null
            );
        }
        
        return savedPayment;
    }
    
    private void updateSaleItemBalance(SaleItem saleItem, BigDecimal paymentAmount, boolean isPaymentSuccess) {
        if (saleItem.getRemainingAmount() == null) {
            BigDecimal totalAmount = saleItem.getPrice().multiply(
                saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE
            );
            saleItem.setRemainingAmount(totalAmount);
        }
        
        if (isPaymentSuccess) {
            BigDecimal newRemainingAmount = saleItem.getRemainingAmount().subtract(paymentAmount);
            if (newRemainingAmount.compareTo(BigDecimal.ZERO) < 0) {
                newRemainingAmount = BigDecimal.ZERO;
            }
            saleItem.setRemainingAmount(newRemainingAmount);
        } else {
            BigDecimal newRemainingAmount = saleItem.getRemainingAmount().add(paymentAmount);
            BigDecimal totalAmount = saleItem.getPrice().multiply(
                saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE
            );
            if (newRemainingAmount.compareTo(totalAmount) > 0) {
                newRemainingAmount = totalAmount;
            }
            saleItem.setRemainingAmount(newRemainingAmount);
        }
        
        updateSaleItemStatus(saleItem);
        saleItemRepository.save(saleItem);
    }
    
    private void updateSaleItemStatus(SaleItem saleItem) {
        if (saleItem.getRemainingAmount() == null) {
            saleItem.setStatus(SaleItemStatus.NOT_PAID);
            return;
        }
        
        BigDecimal totalAmount = saleItem.getPrice().multiply(
            saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE
        );
        
        if (saleItem.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            saleItem.setStatus(SaleItemStatus.FULLY_PAID);
        } else if (saleItem.getRemainingAmount().compareTo(totalAmount) < 0) {
            saleItem.setStatus(SaleItemStatus.PARTIALLY_PAID);
        } else {
            saleItem.setStatus(SaleItemStatus.NOT_PAID);
        }
    }
    
    public void invalidateOwnerCaches(UUID ownerId) {
        try {
            var keys = redisTemplate.keys(CacheKeys.PAYMENTS_MY_PREFIX + ownerId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} payment cache keys for owner {}", keys.size(), ownerId);
            }
        } catch (Exception e) {
            log.warn("Error invalidating payment cache for owner {}: {}", ownerId, e.getMessage());
        }
    }
    
    private void invalidateAllRelatedCachesOnPaymentChange(UUID ownerId, UUID customerId) {
        try {
            invalidateOwnerCaches(ownerId);
            customerService.invalidateCustomerSummaryCache(ownerId, customerId);
            customerServiceConcrete.invalidateOwnerCaches(ownerId);
            statsService.invalidateOwnerCaches(ownerId);
            log.debug("Completed cache invalidation for owner {} on payment change", ownerId);
        } catch (Exception e) {
            log.warn("Error during cache invalidation on payment change for owner {}: {}", ownerId, e.getMessage());
        }
    }

    private void validateOwnerAccess(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        if (ownerId == null || !ownerId.equals(currentOwnerId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only access your own payments");
        }
    }

    /**
     * Resolves customer and product names for a page of payments.
     *
     * The names are always resolved: a payment list that shows only ids is
     * useless to a shopkeeper. It costs three batched lookups for the whole
     * page, not one per row, so there is nothing to gate behind a flag.
     */
    private List<PaymentResponse> mapPaymentResponses(List<Payment> payments) {
        if (payments.isEmpty()) {
            return List.of();
        }
        
        List<UUID> customerIds = payments.stream()
            .map(Payment::getCustomerId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        List<Customer> customers = customerIds.isEmpty() ? List.of() : 
            customerRepository.findByIdIn(customerIds);
        java.util.Map<UUID, String> customerNameMap = customers.stream()
            .collect(java.util.stream.Collectors.toMap(
                Customer::getId,
                Customer::getName,
                (v1, v2) -> v1
            ));
        
        List<UUID> saleItemIds = payments.stream()
            .map(Payment::getSaleItemId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        List<SaleItem> saleItems = saleItemIds.isEmpty() ? List.of() : 
            saleItemRepository.findByIdIn(saleItemIds);
        java.util.Map<UUID, UUID> saleItemProductMap = saleItems.stream()
            .collect(java.util.stream.Collectors.toMap(
                SaleItem::getId,
                SaleItem::getProductId,
                (v1, v2) -> v1
            ));
        
        List<UUID> productIds = saleItems.stream()
            .map(SaleItem::getProductId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        List<Product> products = productIds.isEmpty() ? List.of() : 
            productRepository.findByIdIn(productIds);
        java.util.Map<UUID, String> productNameMap = products.stream()
            .collect(java.util.stream.Collectors.toMap(
                Product::getId,
                Product::getName,
                (v1, v2) -> v1
            ));
        
        return payments.stream()
            .map(payment -> {
                String customerName = customerNameMap.getOrDefault(payment.getCustomerId(), "Unknown");
                
                String productName = null;
                if (payment.getSaleItemId() != null) {
                    UUID productId = saleItemProductMap.get(payment.getSaleItemId());
                    if (productId != null) {
                        productName = productNameMap.getOrDefault(productId, "Unknown");
                    }
                }
                
                return new PaymentResponse(
                    payment.getId(),
                    payment.getCustomerId(),
                    customerName,
                    payment.getOwnerId(),
                    payment.getSaleItemId(),
                    productName,
                    payment.getAmount(),
                    payment.getMedium(),
                    payment.getTimestamp(),
                    payment.getReference(),
                    payment.getStatus(),
                    payment.getCreatedAt(),
                    payment.getUpdatedAt(),
                    Boolean.TRUE.equals(payment.getWriteOff())
                );
            })
            .toList();
    }
}
