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
@Schema(description = "Public owner details response")
public class OwnerPublicResponse {
    @Schema(description = "Owner ID", example = "3595381f-d038-4d6b-8fe0-dc76ebb7dde2")
    private UUID id;
    
    @Schema(description = "Owner name", example = "John Doe")
    private String name;
    
    @Schema(description = "Business name", example = "My Business")
    private String businessName;
    
    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;
    
    @Schema(description = "Email address", example = "john@example.com")
    private String email;
    
    @Schema(description = "Profile image URL", example = "https://example.com/image.jpg")
    private String imageUrl;
    
    @Schema(description = "Verification status", example = "true")
    private Boolean isVerified;
    
    @Schema(description = "Latitude", example = "12.9716")
    private BigDecimal latitude;
    
    @Schema(description = "Longitude", example = "77.5946")
    private BigDecimal longitude;
    
    @Schema(description = "Village", example = "Bangalore")
    private String village;
    
    @Schema(description = "Address text", example = "123 Main Street, Bangalore")
    private String addressText;
    
    @Schema(description = "Distance in kilometers (only present when lat/long query params provided)", example = "5.25")
    private BigDecimal distance;

    @Schema(description = "Public shop slug when listing is enabled")
    private String publicSlug;
}

