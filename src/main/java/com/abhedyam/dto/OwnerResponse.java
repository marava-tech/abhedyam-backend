package com.abhedyam.dto;

import com.abhedyam.model.enums.Subscription;
import com.abhedyam.model.enums.UserType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Owner response")
public class OwnerResponse {
    @Schema(description = "Owner ID (read-only, system-generated)", example = "3595381f-d038-4d6b-8fe0-dc76ebb7dde2", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;
    
    @Schema(description = "Owner name", example = "John Doe")
    private String name;
    
    @Schema(description = "Business name", example = "My Business")
    private String businessName;
    
    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;
    
    @Schema(description = "Email address", example = "john@example.com")
    private String email;
    
    @Schema(description = "User type", example = "BUSINESS")
    private UserType type;
    
    @Schema(description = "Profile image URL", example = "https://example.com/image.jpg")
    private String imageUrl;
    
    @Schema(description = "Verification status", example = "false")
    private Boolean isVerified;
    
    @Schema(description = "Subscription plan", example = "GO")
    private Subscription subscription;

    @Schema(description = "Public shop URL slug")
    private String publicSlug;

    @Schema(description = "Whether the shop is listed publicly")
    private Boolean publicListingEnabled;
    
    @Schema(description = "Creation timestamp (read-only, system-generated)", example = "2025-11-15T10:51:15.325Z", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant createdAt;
    
    @Schema(description = "Last update timestamp (read-only, system-generated)", example = "2025-11-15T10:51:15.325Z", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant updatedAt;
}

