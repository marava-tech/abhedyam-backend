package com.abhedyam.dto;

import java.time.Instant;
import java.util.UUID;

/** One shop in the admin list. Spec: specs/admin-dashboard.md. */
public record AdminShopRow(
        UUID id,
        String name,
        String businessName,
        String email,
        String phone,
        Instant createdAt,
        String status,
        Instant accessUntil,
        long daysLeft,
        String playProductId,
        Instant firstPaidAt,
        long customers,
        long sales30d,
        Instant lastSeenAt,
        Instant lastEntryAt,
        Source source) {

    /** Where the shop came from: another shop's code (REFERRAL) or a partner's code (PARTNER). */
    public record Source(String type, UUID id, String name) {}
}
