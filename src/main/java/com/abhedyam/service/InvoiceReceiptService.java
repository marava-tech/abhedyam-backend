package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.InvoiceResponse;
import com.abhedyam.dto.ReceiptResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Payment;
import com.abhedyam.model.Product;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.model.enums.SaleItemStatus;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceReceiptService {
    
    private final SaleItemRepository saleItemRepository;
    private final CustomerRepository customerRepository;
    private final OwnerRepository ownerRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final LocationDetailsRepository locationDetailsRepository;
    private final UserRepository userRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INVOICE_PREFIX = "ABH-INV-";
    private static final String RECEIPT_PREFIX = "ABH-RCPT-";
    private static final String CREDIT_SALE_NOTE = "Payment for this sale will be collected in parts until the due date.";
    
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByCustomerId(UUID customerId, UUID saleItemId) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        
        UUID ownerId = customer.getOwnerId();
        if (ownerId == null) {
            throw new ResourceNotFoundException("Customer information is incomplete");
        }
        
        validateAccess(currentUserId, customerId, ownerId);
        
        SaleItem requestedSaleItem;
        if (saleItemId != null) {
            requestedSaleItem = saleItemRepository.findById(saleItemId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sale item could not be found"));
            
            if (!requestedSaleItem.getCustomerId().equals(customerId)) {
                throw new BusinessException("INVALID_SALE_ITEM", "This sale item does not belong to this customer");
            }
        } else {
            List<SaleItem> customerSaleItems = saleItemRepository.findByCustomerIdAndOwnerId(customerId, ownerId);
            if (customerSaleItems.isEmpty()) {
                throw new ResourceNotFoundException("No sales found for this customer");
            }
            requestedSaleItem = customerSaleItems.stream()
                    .sorted(Comparator.comparing(SaleItem::getCreatedAt))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No sales found for this customer"));
        }
        
        String transactionId = requestedSaleItem.getTransactionId();
        if (transactionId == null) {
            throw new ResourceNotFoundException("Sale information is incomplete");
        }
        
        List<SaleItem> saleItems;
        if (saleItemId != null) {
            saleItems = List.of(requestedSaleItem);
        } else {
            saleItems = saleItemRepository.findByTransactionId(transactionId);
            if (saleItems.isEmpty()) {
                throw new ResourceNotFoundException("Sale transaction could not be found");
            }
        }
        
        SaleItem firstItem = saleItems.get(0);
        
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Business information could not be found"));
        
        List<Product> products = productRepository.findByOwnerId(ownerId);
        Map<UUID, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        
        LocationDetails customerLocation = locationDetailsRepository.findByUserId(customerId).orElse(null);
        LocationDetails ownerLocation = locationDetailsRepository.findByUserId(ownerId).orElse(null);
        
        Instant invoiceCreatedAt = firstItem.getCreatedAt();
        Instant dueDate = firstItem.getDueDate();
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<InvoiceResponse.InvoiceItem> invoiceItems = new ArrayList<>();
        
        for (SaleItem saleItem : saleItems) {
            Product product = productMap.get(saleItem.getProductId());
            if (product == null) {
                continue;
            }
            
            BigDecimal itemTotal = saleItem.getPrice().multiply(
                saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE
            );
            totalAmount = totalAmount.add(itemTotal);
            
            InvoiceResponse.InvoiceItem item = new InvoiceResponse.InvoiceItem();
            item.setProductId(saleItem.getProductId().toString());
            item.setName(product.getName());
            item.setQuantity(saleItem.getQuantity() != null ? saleItem.getQuantity().intValue() : 1);
            item.setUnitPrice(saleItem.getPrice());
            item.setTotalPrice(itemTotal);
            invoiceItems.add(item);
        }
        
        List<UUID> saleItemIds = saleItems.stream()
                .map(SaleItem::getId)
                .collect(Collectors.toList());
        
        List<Payment> allPayments = paymentRepository.findByCustomerIdAndOwnerId(customerId, ownerId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS 
                    && p.getSaleItemId() != null 
                    && saleItemIds.contains(p.getSaleItemId()))
                .collect(Collectors.toList());
        
        BigDecimal totalPaid = allPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        String invoiceStatus = determineInvoiceStatus(totalPaid, totalAmount);
        
        InvoiceResponse.InvoiceData invoiceData = new InvoiceResponse.InvoiceData();
        invoiceData.setId("inv_" + transactionId.substring(0, 8));
        invoiceData.setInvoiceNumber(generateInvoiceNumber(transactionId));
        invoiceData.setInvoiceDate(formatDate(invoiceCreatedAt));
        invoiceData.setStatus(invoiceStatus);
        
        InvoiceResponse.BusinessInfo businessInfo = new InvoiceResponse.BusinessInfo();
        businessInfo.setId("biz_" + owner.getId().toString().substring(0, 8));
        businessInfo.setName(owner.getBusinessName());
        businessInfo.setPhone(formatPhone(owner.getPhone()));
        businessInfo.setAddress(ownerLocation != null && ownerLocation.getAddressText() != null ? ownerLocation.getAddressText() : "");
        invoiceData.setBusiness(businessInfo);
        
        InvoiceResponse.CustomerInfo customerInfo = new InvoiceResponse.CustomerInfo();
        customerInfo.setId("cust_" + customer.getId().toString().substring(0, 8));
        customerInfo.setName(customer.getName());
        customerInfo.setPhone(formatPhone(customer.getPhone()));
        customerInfo.setVillage(customerLocation != null && customerLocation.getVillage() != null ? customerLocation.getVillage() : "");
        invoiceData.setCustomer(customerInfo);
        
        invoiceData.setItems(invoiceItems);
        
        BigDecimal pendingAmount = totalAmount.subtract(totalPaid);
        
        InvoiceResponse.AmountDetails amountDetails = new InvoiceResponse.AmountDetails();
        amountDetails.setTotal(totalAmount);
        amountDetails.setPaidAmount(totalPaid);
        amountDetails.setPendingAmount(pendingAmount);
        invoiceData.setAmounts(amountDetails);
        
        InvoiceResponse.CreditInfo creditInfo = new InvoiceResponse.CreditInfo();
        creditInfo.setIsCreditSale(true);
        creditInfo.setDueDate(dueDate != null ? formatDate(dueDate) : "");
        creditInfo.setNote(CREDIT_SALE_NOTE);
        invoiceData.setCredit(creditInfo);
        
        invoiceData.setCreatedAt(Instant.now());
        
        InvoiceResponse response = new InvoiceResponse();
        response.setInvoice(invoiceData);
        return response;
    }
    
    @Transactional(readOnly = true)
    public ReceiptResponse getReceiptByCustomerId(UUID customerId, UUID saleItemId) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        
        UUID ownerId = customer.getOwnerId();
        if (ownerId == null) {
            throw new ResourceNotFoundException("Customer information is incomplete");
        }
        
        validateAccess(currentUserId, customerId, ownerId);
        
        SaleItem saleItem;
        if (saleItemId != null) {
            saleItem = saleItemRepository.findById(saleItemId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sale item could not be found"));
            
            if (!saleItem.getCustomerId().equals(customerId)) {
                throw new BusinessException("INVALID_SALE_ITEM", "This sale item does not belong to this customer");
            }
        } else {
            List<SaleItem> customerSaleItems = saleItemRepository.findByCustomerIdAndOwnerId(customerId, ownerId);
            if (customerSaleItems.isEmpty()) {
                throw new ResourceNotFoundException("No sales found for this customer");
            }
            saleItem = customerSaleItems.stream()
                    .sorted(Comparator.comparing(SaleItem::getCreatedAt))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No sales found for this customer"));
        }
        
        UUID saleItemIdForReceipt = saleItem.getId();
        
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Business information could not be found"));
        
        LocationDetails customerLocation = locationDetailsRepository.findByUserId(customerId).orElse(null);
        LocationDetails ownerLocation = locationDetailsRepository.findByUserId(ownerId).orElse(null);
        
        Product product = productRepository.findById(saleItem.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product could not be found"));
        
        String transactionId = saleItem.getTransactionId();
        
        List<Payment> allPayments = paymentRepository.findByCustomerIdAndOwnerId(customerId, ownerId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS 
                    && p.getSaleItemId() != null 
                    && p.getSaleItemId().equals(saleItemIdForReceipt))
                .sorted(Comparator.comparing(Payment::getTimestamp).reversed())
                .collect(Collectors.toList());
        
        if (allPayments.isEmpty()) {
            throw new BusinessException(ErrorCodes.NO_PAYMENTS_FOUND, "No payments found to generate receipt");
        }
        
        BigDecimal totalAmount = saleItem.getPrice().multiply(
            saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ONE
        );
        
        BigDecimal totalPaid = allPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal pendingAmount = totalAmount.subtract(totalPaid);
        
        SaleItemStatus itemStatus = determineSaleItemStatus(totalPaid, totalAmount);
        
        Payment latestPayment = allPayments.isEmpty() ? null : allPayments.get(0);
        Instant receiptDate = latestPayment != null ? latestPayment.getTimestamp() : Instant.now();
        
        List<ReceiptResponse.PaymentHistoryItem> paymentHistory = allPayments.stream()
                .map(p -> {
                    ReceiptResponse.PaymentHistoryItem item = new ReceiptResponse.PaymentHistoryItem();
                    item.setDate(p.getTimestamp());
                    item.setAmount(p.getAmount());
                    item.setMode(p.getMedium() != null ? p.getMedium().name() : "UNKNOWN");
                    return item;
                })
                .collect(Collectors.toList());
        
        ReceiptResponse.ReceiptData receiptData = new ReceiptResponse.ReceiptData();
        receiptData.setId("rcpt_" + saleItemIdForReceipt.toString().substring(0, 8));
        receiptData.setReceiptNumber(generateReceiptNumber(saleItemIdForReceipt));
        receiptData.setReceiptDate(formatDate(receiptDate));
        
        if (transactionId != null) {
            ReceiptResponse.InvoiceReference invoiceRef = new ReceiptResponse.InvoiceReference();
            invoiceRef.setInvoiceNumber(generateInvoiceNumber(transactionId));
            receiptData.setInvoice(invoiceRef);
        }
        
        ReceiptResponse.BusinessInfo businessInfo = new ReceiptResponse.BusinessInfo();
        businessInfo.setName(owner.getBusinessName());
        businessInfo.setPhone(formatPhone(owner.getPhone()));
        businessInfo.setAddress(ownerLocation != null && ownerLocation.getAddressText() != null ? ownerLocation.getAddressText() : "");
        receiptData.setBusiness(businessInfo);
        
        ReceiptResponse.CustomerInfo customerInfo = new ReceiptResponse.CustomerInfo();
        customerInfo.setName(customer.getName());
        customerInfo.setPhone(formatPhone(customer.getPhone()));
        customerInfo.setVillage(customerLocation != null && customerLocation.getVillage() != null ? customerLocation.getVillage() : "");
        receiptData.setCustomer(customerInfo);
        
        ReceiptResponse.ProductInfo productInfo = new ReceiptResponse.ProductInfo();
        productInfo.setName(product.getName());
        receiptData.setProduct(productInfo);
        
        if (latestPayment != null) {
            ReceiptResponse.CurrentPayment currentPayment = new ReceiptResponse.CurrentPayment();
            currentPayment.setAmountPaid(latestPayment.getAmount());
            currentPayment.setMode(latestPayment.getMedium() != null ? latestPayment.getMedium().name() : "UNKNOWN");
            receiptData.setCurrentPayment(currentPayment);
        }
        
        receiptData.setPaymentHistoryTillNow(paymentHistory);
        
        ReceiptResponse.BalanceInfo balanceInfo = new ReceiptResponse.BalanceInfo();
        balanceInfo.setPendingAmount(pendingAmount);
        receiptData.setBalance(balanceInfo);
        
        receiptData.setStatus(itemStatus.name());
        receiptData.setCreatedAt(receiptDate);
        
        ReceiptResponse response = new ReceiptResponse();
        response.setReceipt(receiptData);
        return response;
    }
    
    private SaleItemStatus determineSaleItemStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) == 0) {
            return SaleItemStatus.NOT_PAID;
        } else if (paid.compareTo(total) >= 0) {
            return SaleItemStatus.FULLY_PAID;
        } else {
            return SaleItemStatus.PARTIALLY_PAID;
        }
    }
    
    private String determineInvoiceStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) == 0) {
            return "PENDING";
        } else if (paid.compareTo(total) >= 0) {
            return "PAID";
        } else {
            return "PARTIALLY_PAID";
        }
    }
    
    private void validateAccess(UUID currentUserId, UUID customerId, UUID ownerId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Your account could not be found"));
        
        boolean hasAccess = false;
        if (currentUserId.equals(customerId)) {
            hasAccess = true;
        } else if (currentUser.getType() == UserType.BUSINESS && ownerId != null && ownerId.equals(currentUserId)) {
            hasAccess = true;
        }
        
        if (!hasAccess) {
            throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this");
        }
    }
    
    private String generateInvoiceNumber(String transactionId) {
        String suffix = String.format("%06d", Math.abs(transactionId.hashCode() % 1000000));
        return INVOICE_PREFIX + suffix;
    }
    
    private String generateReceiptNumber(UUID saleItemId) {
        String suffix = String.format("%06d", Math.abs(saleItemId.hashCode() % 1000000));
        return RECEIPT_PREFIX + suffix;
    }
    
    private String formatDate(Instant instant) {
        if (instant == null) {
            return "";
        }
        LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        return date.format(DATE_FORMATTER);
    }

    private String formatPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        return phone;
    }
}

