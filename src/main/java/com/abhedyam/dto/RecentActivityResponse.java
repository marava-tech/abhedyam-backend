package com.abhedyam.dto;

import com.abhedyam.model.enums.AuditAction;
import com.abhedyam.model.enums.AuditType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Recent activity item")
public class RecentActivityResponse {
    @Schema(description = "Activity ID", example = "3595381f-d038-4d6b-8fe0-dc76ebb7dde2", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;
    
    @Schema(description = "Audit type", example = "SALE", accessMode = Schema.AccessMode.READ_ONLY)
    private AuditType type;
    
    @Schema(description = "Audit action", example = "CREATE", accessMode = Schema.AccessMode.READ_ONLY)
    private AuditAction action;
    
    @Schema(description = "Activity headline", example = "SALE CREATE", accessMode = Schema.AccessMode.READ_ONLY)
    private String headline;
    
    @Schema(description = "Activity description", example = "Sale created: transactionId=abc123", accessMode = Schema.AccessMode.READ_ONLY)
    private String description;
    
    @Schema(description = "Amount associated with the activity", example = "1000.00", accessMode = Schema.AccessMode.READ_ONLY)
    private BigDecimal amount;
    
    @Schema(description = "Entity ID related to the activity", example = "3595381f-d038-4d6b-8fe0-dc76ebb7dde2", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID entityId;
    
    @Schema(description = "Customer the activity is about (null for older rows / non-customer events)", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID customerId;

    @Schema(description = "Customer name", example = "Prakash T", accessMode = Schema.AccessMode.READ_ONLY)
    private String customerName;

    @Schema(description = "One short plain line shown when the card is expanded", example = "Rice ×2 · Paid ₹500, due ₹1,900", accessMode = Schema.AccessMode.READ_ONLY)
    private String detail;

    @Schema(description = "Activity timestamp", example = "2025-11-15T10:51:15.325Z", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant timestamp;

    @Schema(description = "Family member / helper who made this (by Ravi); null = the owner")
    private String addedBy;

    public RecentActivityResponse(UUID id, AuditType type, AuditAction action, String headline, String description, BigDecimal amount, UUID entityId, UUID customerId, String customerName, String detail, Instant timestamp) {
        this(id, type, action, headline, description, amount, entityId, customerId, customerName, detail, timestamp, null);
    }
}
