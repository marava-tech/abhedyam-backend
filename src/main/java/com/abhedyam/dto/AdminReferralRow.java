package com.abhedyam.dto;

import java.time.Instant;
import java.util.UUID;

/** One referral (shop → friend shop). Spec: specs/admin-dashboard.md. */
public record AdminReferralRow(
        UUID id,
        ShopRef referrer,
        ShopRef friend,
        String status,
        Instant joinedAt,
        Instant rewardedAt,
        boolean rewardCash,
        long cashRupees,
        Instant cashPaidAt) {

    public record ShopRef(UUID id, String name) {}
}
