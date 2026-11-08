package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Owner list response for admin")
public class AdminOwnerListResponse {
    @Schema(description = "Owner ID", example = "3595381f-d038-4d6b-8fe0-dc76ebb7dde2")
    private UUID id;
    
    @Schema(description = "Owner name", example = "John Doe")
    private String name;
    
    @Schema(description = "Email address", example = "john@example.com")
    private String email;
    
    @Schema(description = "Creation timestamp", example = "2025-11-15T10:51:15.325Z")
    private Instant createdAt;
    
    
    @Schema(description = "Total number of customers", example = "25")
    private Long totalCustomers;

    @Schema(description = "Full access until (trial, paid period or referral days)")
    private java.time.Instant accessUntil;

    @Schema(description = "Google Play product paid for; null = not paying")
    private String playProductId;
}
