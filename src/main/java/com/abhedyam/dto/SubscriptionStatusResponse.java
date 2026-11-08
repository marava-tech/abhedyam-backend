package com.abhedyam.dto;

import com.abhedyam.model.enums.Subscription;
import com.abhedyam.model.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "Current subscription status for the user")
public class SubscriptionStatusResponse {
    
    @Schema(description = "Subscription plan", example = "SAAS")
    private Subscription plan;
    
    @Schema(description = "Subscription status", example = "ACTIVE")
    private SubscriptionStatus status;
    
    @Schema(description = "Subscription valid till date", example = "2027-01-13T00:00:00Z")
    private Instant validTill;
}

