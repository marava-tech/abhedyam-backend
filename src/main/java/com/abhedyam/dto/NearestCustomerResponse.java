package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing nearest customer details")
public class NearestCustomerResponse {
    @Schema(description = "Customer ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID customerId;
    
    @Schema(description = "Customer name", example = "John Doe")
    private String name;
    
    @Schema(description = "Customer phone number", example = "9876543210")
    private String phone;
    
    @Schema(description = "Village name", example = "Koramangala")
    private String village;
    
    @Schema(description = "Product name from most recent sale", example = "Rice 50kg")
    private String productName;
    
    @Schema(description = "Total paid amount", example = "5000.00")
    private BigDecimal paidAmount;
    
    @Schema(description = "Pending amount", example = "2000.00")
    private BigDecimal pendingAmount;
    
    @Schema(description = "Last paid amount", example = "1000.00")
    private BigDecimal lastPaidAmount;
    
    @Schema(description = "Distance from owner location in kilometers, 3 decimals (to the metre)", example = "2.534")
    private BigDecimal distance;
}

