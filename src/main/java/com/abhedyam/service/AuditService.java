package com.abhedyam.service;

import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Audit;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.AuditAction;
import com.abhedyam.model.enums.AuditType;
import com.abhedyam.repository.AuditRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.service.interfaces.IAuditService;
import com.abhedyam.constants.StatusConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService implements IAuditService {
    
    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    
    @Override
    public Audit create(Audit audit) {
        if (audit.getTimestamp() == null) {
            audit.setTimestamp(Instant.now());
        }
        return auditRepository.save(audit);
    }
    
    public Audit getById(UUID id) {
        return auditRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit record could not be found"));
    }
    
    public List<Audit> getAll() {
        return auditRepository.findAll();
    }
    
    public List<Audit> getByOwnerId(UUID ownerId) {
        return auditRepository.findByOwnerId(ownerId);
    }

    @Override
    @Async("virtualThreadExecutor")
    @Transactional
    public void logSaleCreation(UUID saleId, UUID ownerId, UUID customerId, String customerName, BigDecimal amount, String transactionId, String itemsSummary, BigDecimal paid) {
        try {
            Audit audit = new Audit();
            audit.setOwnerId(ownerId);
            audit.setType(AuditType.SALE);
            audit.setAction(AuditAction.CREATE);
            audit.setEntityId(saleId);
            audit.setAmount(amount);
            audit.setCustomerId(customerId);
            audit.setCustomerName(customerName);
            audit.setHeadline(String.format("Sale to %s", customerName));
            audit.setDetail(ActivityText.saleDetail(itemsSummary, paid, amount));
            audit.setDescription(String.format("Sale of %s to %s. %s", ActivityText.money(amount), customerName, audit.getDetail()));
            audit.setTimestamp(Instant.now());
            
            auditRepository.save(audit);
            log.info("Sale creation logged: saleId={}, customerId={}, customerName={}, amount={}", 
                saleId, customerId, customerName, amount);
        } catch (Exception e) {
            log.error("Failed to log sale creation: saleId={}, customerId={}, customerName={}", 
                saleId, customerId, customerName, e);
        }
    }
    
    @Override
    @Async("virtualThreadExecutor")
    @Transactional
    public void logSaleCancellation(UUID saleId, UUID ownerId, UUID customerId, String customerName, BigDecimal amount, String transactionId) {
        try {
            Audit audit = new Audit();
            audit.setOwnerId(ownerId);
            audit.setType(AuditType.SALE);
            audit.setAction(AuditAction.DELETE);
            audit.setEntityId(saleId);
            audit.setAmount(amount.negate());
            audit.setHeadline(String.format("Sale of ₹%s cancelled for %s", amount, customerName));
            audit.setDescription(String.format("Sale transaction cancelled for customer %s. Refunded amount: ₹%s. Transaction ID: %s", 
                customerName, amount, transactionId));
            audit.setTimestamp(Instant.now());
            
            auditRepository.save(audit);
            log.info("Sale cancellation logged: saleId={}, customerId={}, customerName={}, amount={}", 
                saleId, customerId, customerName, amount);
        } catch (Exception e) {
            log.error("Failed to log sale cancellation: saleId={}, customerId={}, customerName={}", 
                saleId, customerId, customerName, e);
        }
    }
    
    @Override
    @Async("virtualThreadExecutor")
    @Transactional
    public void logProductCreation(UUID productId, UUID ownerId, String productName, String productCode) {
        try {
            Audit audit = new Audit();
            audit.setOwnerId(ownerId);
            audit.setType(AuditType.PRODUCT);
            audit.setAction(AuditAction.PRODUCT_CREATED);
            audit.setEntityId(productId);
            audit.setAmount(BigDecimal.ZERO);
            audit.setHeadline(String.format("Product '%s' added to inventory", productName));
            audit.setDescription(String.format("Added new product '%s' to inventory with product code: %s", 
                productName, productCode));
            audit.setTimestamp(Instant.now());
            
            auditRepository.save(audit);
            log.info("Product creation logged: productId={}, name={}, code={}", productId, productName, productCode);
        } catch (Exception e) {
            log.error("Failed to log product creation: productId={}, name={}, code={}", 
                productId, productName, productCode, e);
        }
    }
    
    @Override
    @Async("virtualThreadExecutor")
    @Transactional
    public void logReminderCreation(UUID reminderId, UUID ownerId, UUID customerId, String customerName, 
                                   String reminderName, String reminderText, Instant reminderTime) {
        try {
            Audit audit = new Audit();
            audit.setOwnerId(ownerId);
            audit.setType(AuditType.REMINDER);
            audit.setAction(AuditAction.REMINDER_CREATED);
            audit.setEntityId(reminderId);
            audit.setAmount(BigDecimal.ZERO);
            
            String headline;
            String description;
            
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault());
            String formattedTime = reminderTime != null ? timeFormatter.format(reminderTime) : "scheduled time";
            
            String reminderNameDisplay = reminderName != null && !reminderName.trim().isEmpty() 
                ? reminderName 
                : "Reminder";
            
            String reminderPreview = reminderText != null && reminderText.length() > 150 
                ? reminderText.substring(0, 150) + "..." 
                : (reminderText != null && !reminderText.trim().isEmpty() ? reminderText : "No message provided");
            
            audit.setCustomerId(customerId);
            audit.setCustomerName(customerName);
            audit.setDetail(ActivityText.reminderDetail(reminderTime));

            if (customerId != null && customerName != null) {
                headline = String.format("Reminder set for %s", customerName);
                description = String.format("A reminder has been scheduled for customer %s on %s. " +
                    "The reminder message: \"%s\"", customerName, formattedTime, reminderPreview);
            } else if (ownerId != null) {
                String ownerName = "You";
                try {
                    User owner = userRepository.findById(ownerId).orElse(null);
                    if (owner != null && owner.getName() != null && !owner.getName().trim().isEmpty()) {
                        ownerName = owner.getName();
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch owner name for reminder audit: {}", e.getMessage());
                }
                headline = String.format("Personal reminder '%s' created", reminderNameDisplay);
                String verb = "You".equals(ownerName) ? "have" : "has";
                description = String.format("%s %s created a personal reminder scheduled for %s. " +
                    "Reminder message: \"%s\"", ownerName, verb, formattedTime, reminderPreview);
            } else {
                headline = String.format("Broadcast reminder '%s' created", reminderNameDisplay);
                description = String.format("A broadcast reminder has been scheduled for all users on %s. " +
                    "Message: \"%s\"", formattedTime, reminderPreview);
            }
            
            audit.setHeadline(headline);
            audit.setDescription(description);
            audit.setTimestamp(Instant.now());
            
            auditRepository.save(audit);
            log.info("Reminder creation logged: reminderId={}, name={}, customerId={}, ownerId={}", 
                reminderId, reminderName, customerId, ownerId);
        } catch (Exception e) {
            log.error("Failed to log reminder creation: reminderId={}, customerId={}, ownerId={}", 
                reminderId, customerId, ownerId, e);
        }
    }
    
    @Override
    @Async("virtualThreadExecutor")
    @Transactional
    public void logPaymentSuccess(UUID paymentId, UUID ownerId, UUID customerId, String customerName, UUID saleItemId, String productName, BigDecimal amount, String reference, String medium) {
        try {
            Audit audit = new Audit();
            audit.setOwnerId(ownerId);
            audit.setType(AuditType.PAYMENT);
            audit.setAction(AuditAction.UPDATE);
            audit.setEntityId(paymentId);
            audit.setAmount(amount);
            audit.setCustomerId(customerId);
            audit.setCustomerName(customerName);
            audit.setHeadline(String.format("Payment from %s", customerName));
            audit.setDetail(ActivityText.paymentDetail(medium, productName));
            
            String description = String.format("Received %s from %s for '%s'", 
                ActivityText.money(amount), customerName, productName);
            
            if (reference != null && !reference.trim().isEmpty()) {
                String referenceText = formatPaymentReference(reference);
                description += String.format(". %s", referenceText);
            }
            
            audit.setDescription(description);
            audit.setTimestamp(Instant.now());
            
            auditRepository.save(audit);
            log.info("Payment success logged: paymentId={}, customerId={}, customerName={}, amount={}", 
                paymentId, customerId, customerName, amount);
        } catch (Exception e) {
            log.error("Failed to log payment success: paymentId={}, customerId={}, customerName={}", 
                paymentId, customerId, customerName, e);
        }
    }
    
    @Override
    @Async("virtualThreadExecutor")
    @Transactional
    public void logPaymentCreation(UUID paymentId, UUID ownerId, UUID customerId, String customerName, UUID saleItemId, String productName, BigDecimal amount, String reference, String status, String medium) {
        try {
            Audit audit = new Audit();
            audit.setOwnerId(ownerId);
            audit.setType(AuditType.PAYMENT);
            audit.setAction(AuditAction.CREATE);
            audit.setEntityId(paymentId);
            audit.setAmount(amount);
            
            String statusText = StatusConstants.PENDING.equals(status) ? "Pending" : 
                               StatusConstants.SUCCESS.equals(status) ? "Completed" : 
                               StatusConstants.FAILED.equals(status) ? "Failed" : status;
            
            String mediumText = formatPaymentMedium(medium);
            
            audit.setHeadline(String.format("Payment of ₹%s %s from %s (%s)", amount, statusText.toLowerCase(), customerName, mediumText));
            
            String description = String.format("Payment of ₹%s created with status '%s' from customer %s for product '%s'. Payment method: %s", 
                amount, statusText, customerName, productName, mediumText);
            
            if (reference != null && !reference.trim().isEmpty()) {
                String referenceText = formatPaymentReference(reference);
                if (!referenceText.isEmpty()) {
                    description += String.format(". %s", referenceText);
                }
            }
            
            if (StatusConstants.PENDING.equals(status)) {
                description += ". Payment is pending confirmation and will be processed once verified.";
            } else if (StatusConstants.FAILED.equals(status)) {
                description += ". Payment failed and requires attention.";
            }
            
            audit.setDescription(description);
            audit.setTimestamp(Instant.now());
            
            auditRepository.save(audit);
            log.info("Payment creation logged: paymentId={}, customerId={}, customerName={}, amount={}, status={}, medium={}", 
                paymentId, customerId, customerName, amount, status, medium);
        } catch (Exception e) {
            log.error("Failed to log payment creation: paymentId={}, customerId={}, customerName={}, status={}", 
                paymentId, customerId, customerName, status, e);
        }
    }
    
    @Override
    @Async("virtualThreadExecutor")
    @Transactional
    public void logSubscriptionChange(UUID ownerId, String oldPlan, String newPlan, String oldStatus, String newStatus, String subscriptionId, Instant validTill) {
        try {
            Audit audit = new Audit();
            audit.setOwnerId(ownerId);
            audit.setType(AuditType.SUBSCRIPTION);
            audit.setAction(AuditAction.UPDATE);
            audit.setEntityId(ownerId);
            
            String headline;
            String description;
            
            if (StatusConstants.PRO.equalsIgnoreCase(newPlan) && !StatusConstants.PRO.equalsIgnoreCase(oldPlan)) {
                headline = String.format("Subscription upgraded to PRO plan");
                description = String.format("Subscription upgraded from %s to PRO plan. Status: %s. Subscription ID: %s", 
                    oldPlan != null ? oldPlan : "None", newStatus, subscriptionId != null ? subscriptionId : "N/A");
                if (validTill != null) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a", Locale.ENGLISH)
                        .withZone(ZoneId.systemDefault());
                    description += String.format(". Valid until: %s", formatter.format(validTill));
                }
            } else if (StatusConstants.GO.equalsIgnoreCase(newPlan) && StatusConstants.PRO.equalsIgnoreCase(oldPlan)) {
                headline = String.format("Subscription downgraded to GO plan");
                description = String.format("Subscription downgraded from PRO to GO plan. Previous status: %s, New status: %s. Subscription ID: %s", 
                    oldStatus != null ? oldStatus : "Unknown", newStatus, subscriptionId != null ? subscriptionId : "N/A");
            } else {
                headline = String.format("Subscription changed from %s to %s", 
                    oldPlan != null ? oldPlan : "None", newPlan != null ? newPlan : "None");
                description = String.format("Subscription plan changed from %s to %s. Status changed from %s to %s. Subscription ID: %s", 
                    oldPlan != null ? oldPlan : "None", 
                    newPlan != null ? newPlan : "None",
                    oldStatus != null ? oldStatus : "Unknown",
                    newStatus != null ? newStatus : "Unknown",
                    subscriptionId != null ? subscriptionId : "N/A");
            }
            
            audit.setHeadline(headline);
            audit.setDescription(description);
            audit.setTimestamp(Instant.now());
            
            auditRepository.save(audit);
            log.info("Subscription change logged: ownerId={}, oldPlan={}, newPlan={}, oldStatus={}, newStatus={}", 
                ownerId, oldPlan, newPlan, oldStatus, newStatus);
        } catch (Exception e) {
            log.error("Failed to log subscription change: ownerId={}, oldPlan={}, newPlan={}", 
                ownerId, oldPlan, newPlan, e);
        }
    }
    
    private String formatPaymentMedium(String medium) {
        if (medium == null || medium.trim().isEmpty()) {
            return "Unknown";
        }
        return switch (medium.toUpperCase()) {
            case "CASH" -> "Cash";
            case "UPI" -> "UPI";
            case "PHONEPE" -> "PhonePe";
            case "PAYTM" -> "Paytm";
            case "OFFLINE" -> "Offline";
            case "IN_APP" -> "In-App";
            default -> medium;
        };
    }
    
    private String formatPaymentReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return "";
        }
        
        String trimmedRef = reference.trim();
        
        if (isImageUrl(trimmedRef)) {
            return "Payment receipt image uploaded";
        }
        
        if (isTransactionId(trimmedRef)) {
            return String.format("Transaction ID: %s", trimmedRef);
        }
        
        if (trimmedRef.length() > 50) {
            return String.format("Reference: %s...", trimmedRef.substring(0, 50));
        }
        
        return String.format("Reference: %s", trimmedRef);
    }
    
    private boolean isImageUrl(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lowerText = text.toLowerCase();
        return lowerText.startsWith("http://") || lowerText.startsWith("https://") ||
               lowerText.contains(".jpg") || lowerText.contains(".jpeg") || 
               lowerText.contains(".png") || lowerText.contains(".gif") ||
               lowerText.contains("cloudinary") || lowerText.contains("image") ||
               lowerText.contains("/upload/");
    }
    
    private boolean isTransactionId(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String upperText = text.toUpperCase();
        return upperText.startsWith("TXN") || upperText.startsWith("UPI") ||
               upperText.startsWith("PAY") || (text.length() >= 10 && text.matches(".*\\d{10,}.*"));
    }

    @Transactional
    public Audit update(UUID id, Audit auditDetails) {
        Audit audit = getById(id);
        if (auditDetails.getHeadline() != null) audit.setHeadline(auditDetails.getHeadline());
        if (auditDetails.getDescription() != null) audit.setDescription(auditDetails.getDescription());
        return auditRepository.save(audit);
    }
}
