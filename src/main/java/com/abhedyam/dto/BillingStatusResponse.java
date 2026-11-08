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
        @Schema(description = "Founding-price seats left (₹499/yr for life) out of 100", example = "63") long foundingLeft) {
}
