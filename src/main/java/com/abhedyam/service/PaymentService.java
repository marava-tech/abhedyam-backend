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
import com.abhedyam.model.User;
import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.model.enums.SaleItemStatus;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.model.Notification;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.service.interfaces.IAuditService;
import com.abhedyam.service.interfaces.ICustomerService;
import com.abhedyam.service.interfaces.IFcmService;
import com.abhedyam.service.interfaces.INotificationService;
import com.abhedyam.service.interfaces.IPaymentService;
import com.abhedyam.util.PackageConstants;
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
    private final UserRepository userRepository;
    private final IAuditService auditService;
    private final INotificationService notificationService;
    private final IFcmService fcmService;
    private final ICustomerService customerService;
    private final CustomerService customerServiceConcrete;
    private final RedisTemplate<String, String> redisTemplate;
    private final StatsService statsService;
    
    
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
        UUID saleItemCustomerId = saleItem.getCustomerId();
        
        // Allow access if:
        // 1. Current user is the customer of the sale item
        // 2. Current user is the owner of the sale item
        boolean hasAccess = false;
        UUID paymentOwnerId = saleItemOwnerId;
        
        if (currentUserId.equals(saleItemCustomerId)) {
            hasAccess = true;
            paymentOwnerId = saleItemOwnerId;
        } else if (saleItemOwnerId != null && saleItemOwnerId.equals(currentUserId)) {
            User currentUser = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Your account could not be found"));
            if (currentUser.getType() == UserType.BUSINESS) {
                hasAccess = true;
                paymentOwnerId = currentUserId;
            }
        }
        
        if (!hasAccess) {
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
        payment.setOwnerId(paymentOwnerId);
        payment.setSaleItemId(request.getSaleItemId());
        payment.setAmount(request.getAmount());
        payment.setMedium(request.getMedium());
        payment.setTimestamp(Instant.now());
        payment.setReference(request.getReference());
        payment.setStatus(request.getStatus() != null ? request.getStatus() : PaymentStatus.SUCCESS);
        
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
                savedPayment.getReference()
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
        
        if (savedPayment.getStatus() == PaymentStatus.PENDING && currentUserId.equals(saleItemCustomerId)) {
            sendPendingPaymentNotificationToOwner(savedPayment, customer, product, paymentOwnerId);
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
                savedPayment.getUpdatedAt()
        );
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
                savedPayment.getReference()
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
                savedPayment.getUpdatedAt()
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public Payment getById(UUID id) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment could not be found"));
        
        UUID paymentOwnerId = payment.getOwnerId();
        UUID paymentCustomerId = payment.getCustomerId();
        
        // Allow access if:
        // 1. Current user is the customer of the payment
        // 2. Current user is the owner of the payment
        boolean hasAccess = false;
        
        if (currentUserId.equals(paymentCustomerId)) {
            hasAccess = true;
        } else if (paymentOwnerId != null && paymentOwnerId.equals(currentUserId)) {
            User currentUser = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Your account could not be found"));
            if (currentUser.getType() == UserType.BUSINESS) {
                hasAccess = true;
            }
        }
        
        if (!hasAccess) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this payment");
        }
        
        return payment;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Payment> getByOwnerId(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        UUID targetOwnerId = ownerId != null ? ownerId : currentOwnerId;
        if (!currentOwnerId.equals(targetOwnerId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only access your own payments");
        }
        return paymentRepository.findByOwnerId(targetOwnerId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getMyPayments(Integer page, Integer size) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Payment> paymentPage = paymentRepository.findByOwnerId(ownerId, pageable);
        List<Payment> payments = paymentPage.getContent();
        
        if (payments.isEmpty()) {
            return new PageResponse<>(
                List.of(),
                paymentPage.getNumber(),
                paymentPage.getSize(),
                paymentPage.getTotalElements(),
                paymentPage.getTotalPages(),
                paymentPage.hasNext(),
                paymentPage.hasPrevious()
            );
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
        
        List<PaymentResponse> responses = payments.stream()
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
                            payment.getUpdatedAt()
                    );
                })
                .toList();
        
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
                            payment.getUpdatedAt()
                    );
                })
                .toList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Payment> getByCustomerId(UUID customerId) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        
        UUID ownerId = customer.getOwnerId();
        
        // Allow access if:
        // 1. Current user is the customer themselves
        // 2. Current user is the owner of this customer
        boolean hasAccess = false;
        
        if (currentUserId.equals(customerId)) {
            hasAccess = true;
        } else if (ownerId != null) {
            User currentUser = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Your account could not be found"));
            
            if (currentUser.getType() == UserType.BUSINESS && ownerId.equals(currentUserId)) {
                hasAccess = true;
            }
        }
        
        if (!hasAccess) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this customer's payments");
        }
        
        // If customer is accessing, use their ownerId; if owner is accessing, use currentUserId
        UUID filterOwnerId = currentUserId.equals(customerId) ? ownerId : currentUserId;
        if (filterOwnerId == null) {
            return List.of();
        }
        
        List<Payment> payments = paymentRepository.findByCustomerIdAndOwnerId(customerId, filterOwnerId);
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
                payment.getReference()
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
    
    private void sendPendingPaymentNotificationToOwner(Payment payment, Customer customer, Product product, UUID ownerId) {
        try {
            String message = String.format("You have a payment pending please verify. Customer: %s, Amount: ₹%s, Product: %s", 
                customer.getName(), payment.getAmount(), product.getName());
            
            Notification notification = new Notification();
            notification.setOwnerId(ownerId);
            notification.setUserId(ownerId);
            notification.setType(NotificationType.ACTION_REQUIRED);
            notification.setMessage(message);
            notification.setTimestamp(Instant.now());
            notification.setIsRead(false);
            notification.setRelatedEntityId(payment.getId());
            notification.setRelatedEntityType("PAYMENT");
            notification.setRetryCount(0);
            
            notificationService.create(notification);
            log.info("In-app notification created for pending payment {} to owner {}", payment.getId(), ownerId);
            
            String title = "Payment Pending Verification";
            String body = String.format("%s has a payment of ₹%s pending verification", customer.getName(), payment.getAmount());
            
            fcmService.sendNotificationToUser(ownerId, title, body, PackageConstants.BUSINESS_APP_PACKAGE);
            log.info("FCM notification sent for pending payment {} to owner {}", payment.getId(), ownerId);
        } catch (Exception e) {
            log.error("Failed to send notification for pending payment {} to owner {}: {}", 
                payment.getId(), ownerId, e.getMessage(), e);
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
                    payment.getUpdatedAt()
                );
            })
            .toList();
    }
}
