package com.abhedyam.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import com.abhedyam.model.enums.PaymentMedium;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Request to create a sale with multiple items")
public class SaleCreateRequest {
    @Schema(description = "UUID of the customer (if not provided, customer will be created with name, phone, and village)", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID customerId;
    
    @Schema(description = "Customer name (required if customerId is not provided)", example = "John Doe")
    private String customerName;
    
    @Schema(description = "Customer phone (optional, used only if creating new customer)", example = "+919876543210")
    private String customerPhone;
    
    @Schema(description = "Customer village (optional, used only if creating new customer)", example = "Koramangala")
    private String customerVillage;
    
    @NotEmpty(message = "At least one sale item is required")
    @Schema(description = "List of sale items", required = true)
    private List<SaleItemRequest> items;
    
    @Schema(description = "Due date for payment", example = "2024-12-31T23:59:59Z")
    @JsonDeserialize(using = FlexibleInstantDeserializer.class)
    private Instant dueDate;

    @PositiveOrZero(message = "Paid amount cannot be negative")
    @Schema(description = "Amount paid at the time of sale; the rest stays due", example = "500.00")
    private BigDecimal paidAmount;

    @Schema(description = "How the paid amount was received (default CASH)", example = "UPI")
    private PaymentMedium paymentMedium;

    @Schema(description = "Sell to the owner's Walk-in customer (must be paid in full)")
    private Boolean walkIn;

    @Size(max = 64)
    @Schema(description = "Client-generated key; a repeat of the same key returns the first sale")
    private String idempotencyKey;
}
