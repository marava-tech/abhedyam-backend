package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityTextTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void moneyUsesIndianGroupingAndDropsNeedlessDecimals() {
        assertEquals("₹16,000", ActivityText.money(d("16000.00")));
        assertEquals("₹4,44,180", ActivityText.money(d("444180")));
        assertEquals("₹1,234.50", ActivityText.money(d("1234.5")));
        assertEquals("₹1", ActivityText.money(d("1.0")));
        assertEquals("₹0", ActivityText.money(null));
    }

    @Test
    void itemsSummaryShowsQuantityOnlyWhenNotOneAndCapsAtThree() {
        var lines = List.of(new ActivityText.Line("Rice", d("2")), new ActivityText.Line("Oil", d("1")),
                new ActivityText.Line("Sugar", d("2.5")), new ActivityText.Line("Dal", d("1")),
                new ActivityText.Line("Salt", d("1")));
        assertEquals("Rice ×2, Oil, Sugar ×2.5 +2 more", ActivityText.itemsSummary(lines));
        assertEquals("", ActivityText.itemsSummary(List.of()));
    }

    @Test
    void saleDetailSaysHowItWasPaid() {
        assertEquals("Rice ×2 · On udhar", ActivityText.saleDetail("Rice ×2", null, d("2400")));
        assertEquals("Rice ×2 · Paid in full", ActivityText.saleDetail("Rice ×2", d("2400"), d("2400")));
        assertEquals("Rice ×2 · Paid ₹500, due ₹1,900", ActivityText.saleDetail("Rice ×2", d("500"), d("2400")));
        assertEquals("On udhar", ActivityText.saleDetail("", d("0"), d("100")));
    }

    @Test
    void paymentDetailNamesMediumAndProduct() {
        assertEquals("Cash · for Rice", ActivityText.paymentDetail("CASH", "Rice"));
        assertEquals("UPI", ActivityText.paymentDetail("UPI", null));
        assertEquals("for Rice", ActivityText.paymentDetail(null, "Rice"));
        assertNull(ActivityText.paymentDetail(null, " "));
    }

    @Test
    void reminderDetailUsesIndianTimeAndClipsLongText() {
        assertEquals("Due 12 Nov, 5:00 PM", ActivityText.reminderDetail(Instant.parse("2026-11-12T11:30:00Z")));
        assertNull(ActivityText.reminderDetail(null));
        assertEquals(255, ActivityText.clip("x".repeat(400)).length());
    }

    @Test
    void saleFactsKeepFirstThreeItemsCountTheRestAndRoundTrip() {
        String json = ActivityText.saleFacts(List.of(
                new ActivityText.Line("Rice", new BigDecimal("2.00")),
                new ActivityText.Line("Oil", BigDecimal.ONE),
                new ActivityText.Line("Sugar", new BigDecimal("1.5")),
                new ActivityText.Line("Dal", BigDecimal.ONE),
                new ActivityText.Line("Salt", BigDecimal.ONE)),
                new BigDecimal("500"), new BigDecimal("1200"));
        var f = ActivityText.read(json);
        var items = (List<?>) f.get("items");
        assertEquals(3, items.size());
        assertEquals("Rice", ((java.util.Map<?, ?>) items.get(0)).get("name"));
        assertEquals(2, ((Number) ((java.util.Map<?, ?>) items.get(0)).get("qty")).intValue());
        assertEquals(2, ((Number) f.get("more")).intValue());
        assertEquals(500, ((Number) f.get("paid")).intValue());
        assertEquals(1200, ((Number) f.get("total")).intValue());
        assertNull(ActivityText.read(null));
        assertNull(ActivityText.read("not json"));
    }
}
