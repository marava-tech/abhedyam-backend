package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "The owner's referral code and the shops that joined with it")
public record ReferralSummaryResponse(
        @Schema(example = "ABH8X29K") String referralCode,
        @Schema(example = "https://duesetu.com/join?ref=ABH8X29K") String shareLink,
        @Schema(description = "Free months earned (friends who paid while you were not paying)") long monthsEarned,
        @Schema(description = "₹ owed to you by UPI (friends who paid while you were paying)") long cashEarned,
        @Schema(description = "This owner already used someone's code") boolean usedACode,
        List<Friend> friends) {

    public record Friend(String businessName, Instant joinedAt, boolean paid) {
    }
}
