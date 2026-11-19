package com.abhedyam.dto;

import com.abhedyam.model.enums.SaleItemStatus;
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
@Schema(description = "Sale item response with product name")
public class SaleItemResponse {
    @Schema(description = "Sale item ID")
    private UUID id;
    
    @Schema(description = "Product ID")
    private UUID productId;
    
    @Schema(description = "Product name")
    private String productName;
    
    @Schema(description = "Customer ID")
    private UUID customerId;
    
    @Schema(description = "Owner ID")
    private UUID ownerId;
    
    @Schema(description = "Price per unit")
    private BigDecimal price;
    
    @Schema(description = "Quantity")
    private BigDecimal quantity;
    
    @Schema(description = "Remaining amount to be paid")
    private BigDecimal remainingAmount;
    
    @Schema(description = "Payment status")
    private SaleItemStatus status;
    
    @Schema(description = "Due date")
    private Instant dueDate;
    
    @Schema(description = "Transaction ID")
    private String transactionId;
    
    @Schema(description = "Creation timestamp")
    private Instant createdAt;
    
    @Schema(description = "Update timestamp")
    private Instant updatedAt;
    
    @Schema(description = "Is active")
    private Boolean isActive;

    @Schema(description = "True = old balance from before Abhedyam, not a sale")
    private Boolean oldBalance;
}

