package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "The owner's access: trial, paid or read-only")
public record BillingStatusResponse(
        @Schema(description = "Full access until this time; after it the app is read-only") Instant accessUntil,
        @Schema(description = "accessUntil is in the future") boolean active,
        @Schema(description = "Pays on Google Play right now") boolean paying,
        @Schema(description = "Google Play product paid for, null when not paying", example = "abhedyam_yearly") String playProductId,
        @Schema(description = "Whole days of access left (0 when read-only)", example = "23") long daysLeft,
        @Schema(description = "Founding-price seats left out of 100 (first year at the founding price)", example = "63") long foundingLeft,
        @Schema(description = "SHOP (also during the trial) or BUSINESS", example = "SHOP") String plan,
        @Schema(description = "Phones the owner can be signed in on at once", example = "1") int maxPhones,
        @Schema(description = "Family members + helpers the shop can have", example = "2") int maxHelpers,
        @Schema(description = "The second free month was earned (10 customers + 5 payments)") boolean extraMonthEarned) {
}
