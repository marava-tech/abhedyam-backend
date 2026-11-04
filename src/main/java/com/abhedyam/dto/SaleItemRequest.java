package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "Sale item details")
public class SaleItemRequest {
    @Schema(description = "UUID of the product (if not provided, product will be created with name and price)", example = "123e4567-e89b-12d3-a456-426614174001")
    private UUID productId;
    
    @Schema(description = "Product name (required if productId is not provided)", example = "Sample Product")
    private String productName;
    
    @Positive(message = "Price must be positive")
    @Schema(description = "Price per unit (required if productId is not provided, otherwise product price will be used if not provided)", example = "1000.00")
    private BigDecimal price;

    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
    @DecimalMax(value = "99999.999", message = "Quantity is too large")
    @Digits(integer = 5, fraction = 3, message = "Quantity can have at most 3 decimals")
    @Schema(description = "Units sold (default 1)", example = "2.5")
    private BigDecimal quantity;
}
