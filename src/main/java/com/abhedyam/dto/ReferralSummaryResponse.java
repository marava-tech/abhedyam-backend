package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary of owner's referral stats, share link, and referred business list")
public class ReferralSummaryResponse {

    @Schema(description = "The owner's unique referral code", example = "ABH8X29K")
    private String referralCode;

    @Schema(description = "1-Tap share link", example = "https://duesetu.com/join?ref=ABH8X29K")
    private String shareLink;

    @Schema(description = "Total businesses invited", example = "4")
    private long totalReferred;

    @Schema(description = "Months of PRO earned from referrals", example = "2")
    private long rewardedMonths;

    @Schema(description = "Referrals currently progressing towards qualification", example = "2")
    private long pendingCount;

    @Schema(description = "List of referred businesses with their live progress")
    private List<ReferredOwnerProgressDto> referrals;
}
