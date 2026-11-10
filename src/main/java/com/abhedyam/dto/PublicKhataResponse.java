package com.abhedyam.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the customer sees on duesetu.com/k/<token>. Never holds the customer's phone. */
public record PublicKhataResponse(
        String shopName,
        String shopPhone,
        String shopImageUrl,
        String customerName,
        BigDecimal balance,
        NextDue nextDue,
        long overdueCount,
        String upiId,
        List<Entry> entries,
        Instant generatedAt) {

    public record NextDue(BigDecimal amount, BigDecimal remaining, LocalDate dueDate, String status) {
    }

    /** kind: SALE, PAYMENT or WRITE_OFF. label: product name for a sale (may be null), payment method for a payment. */
    public record Entry(String kind, String label, BigDecimal amount, Instant at) {
    }
}
