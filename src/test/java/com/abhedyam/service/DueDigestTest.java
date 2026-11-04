package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.SaleItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DueDigestTest {
    final Instant now = Instant.parse("2026-11-04T03:00:00Z");
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    SaleItem item(UUID customer, int daysAgo, SaleItemStatus status) {
        SaleItem i = new SaleItem();
        i.setCustomerId(customer);
        i.setStatus(status);
        i.setCreatedAt(now.minus(daysAgo, ChronoUnit.DAYS));
        return i;
    }

    @Test
    void sumsOnlyWhatEachCustomerOwesAndFindsTheOldestUnpaidSale() {
        Map<UUID, BigDecimal> sales = Map.of(a, new BigDecimal("100000"), b, new BigDecimal("24000"), c, new BigDecimal("5000"));
        Map<UUID, BigDecimal> paid = Map.of(c, new BigDecimal("5000")); // c is settled
        var items = List.of(item(a, 45, SaleItemStatus.NOT_PAID), item(b, 10, SaleItemStatus.PARTIALLY_PAID), item(c, 90, SaleItemStatus.FULLY_PAID));
        DueDigest d = DueDigest.of(sales, paid, items, now);
        assertEquals(2, d.customers());
        assertEquals(new BigDecimal("124000"), d.total());
        assertEquals(45, d.oldestDays()); // c's old sale is paid, so it does not count
        assertEquals("2 customers owe ₹1,24,000 · oldest 45 days", d.text());
    }

    @Test
    void anOverpaidCustomerDoesNotOffsetSomeoneElse() {
        Map<UUID, BigDecimal> sales = Map.of(a, new BigDecimal("1000"), b, new BigDecimal("500"));
        Map<UUID, BigDecimal> paid = Map.of(a, new BigDecimal("900"), b, new BigDecimal("800")); // b paid 300 extra
        DueDigest d = DueDigest.of(sales, paid, List.of(item(a, 3, SaleItemStatus.PARTIALLY_PAID)), now);
        assertEquals(1, d.customers());
        assertEquals(new BigDecimal("100"), d.total());
        assertEquals("1 customer owes ₹100 · oldest 3 days", d.text());
    }

    @Test
    void nobodyOwingMeansNoDigest() {
        assertNull(DueDigest.of(Map.of(a, BigDecimal.TEN), Map.of(a, BigDecimal.TEN), List.of(), now));
        assertNull(DueDigest.of(Map.of(), Map.of(), List.of(), now));
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
        DueDigest d = DueDigest.of(Map.of(a, BigDecimal.TEN), Map.of(), List.of(item(a, 1, SaleItemStatus.NOT_PAID)), now);
        assertTrue(d.text().endsWith("oldest 1 day"));
    }
}
