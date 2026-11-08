package com.abhedyam.dto;

import com.abhedyam.model.enums.UserType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response")
public class AuthResponse {
    @Schema(description = "JWT token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;
    
    @Schema(description = "User ID", example = "3595381f-d038-4d6b-8fe0-dc76ebb7dde2")
    private String userId;
    
    @Schema(description = "Phone number or email", example = "+919876543210")
    private String phone;
    
    @Schema(description = "User name", example = "John Doe")
    private String name;
    
    @Schema(description = "Whether this is a new user", example = "false")
    private Boolean isNewUser;
    
    @Schema(description = "User type", example = "BUSINESS", allowableValues = {"CUSTOMER", "BUSINESS"})
    private UserType userType;
    
    @Schema(description = "Owner ID (for customers)", example = "3595381f-d038-4d6b-8fe0-dc76ebb7dde2")
    private UUID ownerId;
    
    @Schema(description = "Account creation timestamp", example = "2024-01-15T10:30:00Z")
    private Instant accountCreatedAt;

    @Schema(description = "Family member / helper signed in (userId is then the shop owner's id); null = the owner")
    private UUID memberId;

    @Schema(description = "FAMILY or HELPER; null = the owner")
    private String memberRole;

    @Schema(description = "Shop name, for a member's app")
    private String shopName;

    public AuthResponse(String token, String userId, String phone, String name, Boolean isNewUser, UserType userType,
                        UUID ownerId, Instant accountCreatedAt) {
        this(token, userId, phone, name, isNewUser, userType, ownerId, accountCreatedAt, null, null, null);
    }
}

