package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DueDigestTest {
    final Instant now = Instant.parse("2026-11-04T03:00:00Z");
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    Instant daysAgo(int d) {
        return now.minus(d, ChronoUnit.DAYS);
    }

    @Test
    void sumsOnlyWhatEachCustomerOwesAndFindsTheOldestUnpaidSale() {
        Map<UUID, BigDecimal> sales = Map.of(a, new BigDecimal("100000"), b, new BigDecimal("24000"), c, new BigDecimal("5000"));
        Map<UUID, BigDecimal> paid = Map.of(c, new BigDecimal("5000")); // c is settled
        // c's 90-day-old sale belongs to someone who owes nothing, so it does not count
        DueDigest d = DueDigest.of(sales, paid, Map.of(a, daysAgo(45), b, daysAgo(10), c, daysAgo(90)), now);
        assertEquals(2, d.customers());
        assertEquals(new BigDecimal("124000"), d.total());
        assertEquals(45, d.oldestDays());
        assertEquals("2 customers owe ₹1,24,000 · oldest 45 days", d.text());
    }

    @Test
    void anOverpaidCustomerDoesNotOffsetSomeoneElse() {
        Map<UUID, BigDecimal> sales = Map.of(a, new BigDecimal("1000"), b, new BigDecimal("500"));
        Map<UUID, BigDecimal> paid = Map.of(a, new BigDecimal("900"), b, new BigDecimal("800")); // b paid 300 extra
        DueDigest d = DueDigest.of(sales, paid, Map.of(a, daysAgo(3)), now);
        assertEquals(1, d.customers());
        assertEquals(new BigDecimal("100"), d.total());
        assertEquals("1 customer owes ₹100 · oldest 3 days", d.text());
    }

    @Test
    void nobodyOwingMeansNoDigest() {
        assertNull(DueDigest.of(Map.of(a, BigDecimal.TEN), Map.of(a, BigDecimal.TEN), Map.of(), now));
        assertNull(DueDigest.of(Map.of(), Map.of(), Map.of(), now));
    }

    @Test
    void indianGrouping() {
        assertEquals("0", DueDigest.indian(0));
        assertEquals("999", DueDigest.indian(999));
        assertEquals("1,000", DueDigest.indian(1000));
        assertEquals("12,345", DueDigest.indian(12345));
        assertEquals("1,24,000", DueDigest.indian(124000));
        assertEquals("12,34,567", DueDigest.indian(1234567));
        assertEquals("1,00,00,000", DueDigest.indian(10000000));
    }

    @Test
    void singularDay() {
        DueDigest d = DueDigest.of(Map.of(a, BigDecimal.TEN), Map.of(), Map.of(a, daysAgo(1)), now);
        assertTrue(d.text().endsWith("oldest 1 day"));
    }
}
