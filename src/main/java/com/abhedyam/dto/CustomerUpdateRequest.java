package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to update customer details")
public class CustomerUpdateRequest {
    @Schema(description = "Customer name", example = "John Doe")
    private String name;
    
    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;
    
    @Schema(description = "Village name", example = "Koramangala")
    private String village;
    
    @Schema(description = "Latitude", example = "12.9352")
    private BigDecimal latitude;
    
    @Schema(description = "Longitude", example = "77.6245")
    private BigDecimal longitude;
}
