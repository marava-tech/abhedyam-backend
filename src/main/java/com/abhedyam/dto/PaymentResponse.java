package com.abhedyam.dto;

import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
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
@Schema(description = "Payment response with customer and product names")
public class PaymentResponse {
    @Schema(description = "Payment ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;
    
    @Schema(description = "Customer ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID customerId;
    
    @Schema(description = "Customer name", example = "John Doe")
    private String customerName;
    
    @Schema(description = "Owner ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID ownerId;
    
    @Schema(description = "Sale Item ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID saleItemId;
    
    @Schema(description = "Product name", example = "Rice")
    private String productName;
    
    @Schema(description = "Payment amount", example = "1000.00")
    private BigDecimal amount;
    
    @Schema(description = "Payment medium", example = "UPI")
    private PaymentMedium medium;
    
    @Schema(description = "Payment timestamp")
    private Instant timestamp;
    
    @Schema(description = "Payment reference", example = "ORDER_ABC123")
    private String reference;
    
    @Schema(description = "Payment status", example = "SUCCESS")
    private PaymentStatus status;
    
    @Schema(description = "Created timestamp")
    private Instant createdAt;
    
    @Schema(description = "Updated timestamp")
    private Instant updatedAt;

    @Schema(description = "Written off by the owner: closes the due, not money received", example = "false")
    private boolean writeOff;
}

