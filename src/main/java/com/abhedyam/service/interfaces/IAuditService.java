package com.abhedyam.service.interfaces;

import com.abhedyam.model.Audit;
import com.abhedyam.model.enums.AuditAction;
import com.abhedyam.model.enums.AuditType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IAuditService {
    Audit create(Audit audit);
    Audit getById(UUID id);
    List<Audit> getAll();
    List<Audit> getByOwnerId(UUID ownerId);
    Audit update(UUID id, Audit auditDetails);
    void logFinancialOperation(AuditType type, AuditAction action, UUID entityId, 
                              UUID ownerId, BigDecimal amount, String details);
    void logStockChange(UUID productId, UUID ownerId, String productName, BigDecimal oldStock, 
                       BigDecimal newStock, String source, String details);
    void logSaleCreation(UUID saleId, UUID ownerId, UUID customerId, String customerName, BigDecimal amount, String transactionId, String itemsSummary, BigDecimal paid);
    void logSaleCancellation(UUID saleId, UUID ownerId, UUID customerId, String customerName, BigDecimal amount, String transactionId);
    void logProductCreation(UUID productId, UUID ownerId, String productName, String productCode);
    void logReminderCreation(UUID reminderId, UUID ownerId, UUID customerId, String customerName, String reminderName, String reminderText, Instant reminderTime);
    void logPaymentSuccess(UUID paymentId, UUID ownerId, UUID customerId, String customerName, UUID saleItemId, String productName, BigDecimal amount, String reference, String medium);
    void logPaymentCreation(UUID paymentId, UUID ownerId, UUID customerId, String customerName, UUID saleItemId, String productName, BigDecimal amount, String reference, String status, String medium);
    void logSubscriptionChange(UUID ownerId, String oldPlan, String newPlan, String oldStatus, String newStatus, String subscriptionId, Instant validTill);
}

