package com.abhedyam.dto;

import com.abhedyam.model.enums.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to update owner details")
public class OwnerUpdateRequest {
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
    
    @Schema(description = "Subscription plan", example = "GO")
    private Subscription subscription;
    
    @Schema(description = "Verification status", example = "false")
    private Boolean isVerified;

    @Schema(description = "Public shop URL slug", example = "ravi-kirana")
    private String publicSlug;

    @Schema(description = "Whether the shop appears on public listing pages")
    private Boolean publicListingEnabled;
}

