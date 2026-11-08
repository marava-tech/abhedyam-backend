package com.abhedyam.dto;

import java.time.Instant;
import java.util.UUID;

public record PartnerRow(
        UUID id,
        String name,
        String phone,
        String upiId,
        String code,
        String shareLink,
        Instant createdAt,
        long shopsJoined,
        long shopsPaid,
        long owedRupees,
        long paidRupees) {
}
