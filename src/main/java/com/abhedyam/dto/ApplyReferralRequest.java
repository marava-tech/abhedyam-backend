package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "A friend's referral code")
public record ApplyReferralRequest(
        @NotBlank(message = "Enter a referral code") @Schema(example = "ABH8X29K") String referralCode) {
}
