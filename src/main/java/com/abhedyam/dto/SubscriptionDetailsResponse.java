package com.abhedyam.dto;

import com.abhedyam.model.enums.Subscription;
import com.abhedyam.model.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Detailed subscription information for an owner")
public class SubscriptionDetailsResponse {
    
    @Schema(description = "Owner ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID ownerId;
    
    @Schema(description = "Subscription plan", example = "SAAS")
    private Subscription plan;
    
    @Schema(description = "Subscription status", example = "ACTIVE")
    private SubscriptionStatus status;
    
    @Schema(description = "Subscription valid till date", example = "2027-01-13T00:00:00Z")
    private Instant validTill;
    
    @Schema(description = "Razorpay order ID", example = "order_1234567890")
    private String razorpayOrderId;
    
    @Schema(description = "Subscription activated at", example = "2026-01-13T00:00:00Z")
    private Instant activatedAt;
    
    @Schema(description = "Subscription expired at", example = "2027-01-13T00:00:00Z")
    private Instant expiredAt;
    
    @Schema(description = "Subscription cancelled at", example = "2027-01-13T00:00:00Z")
    private Instant cancelledAt;
}

