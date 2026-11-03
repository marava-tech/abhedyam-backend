package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VillagePendingCalculatorTest {
    @Test
    void sumsPositiveDuesIgnoresOverpaidAndMatchesVillageCaseInsensitively() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        Map<UUID, BigDecimal> sales = Map.of(
                a, new BigDecimal("1000"), b, new BigDecimal("500"),
                c, new BigDecimal("200"), d, new BigDecimal("300"));
        Map<UUID, BigDecimal> paid = Map.of(a, new BigDecimal("400"), c, new BigDecimal("900"));
        Map<UUID, String> villages = Map.of(a, "Rampur", b, " rampur ", c, "Rampur", d, "Kondapur");

        Map<String, BigDecimal> r = VillagePendingCalculator.pendingByVillage(sales, paid, villages);

        // a: 600, b: 500, c: overpaid -> 0 (not -700)
        assertEquals(0, new BigDecimal("1100").compareTo(r.get("rampur")));
        assertEquals(0, new BigDecimal("300").compareTo(r.get("kondapur")));
        assertNull(r.get("unknown"));
    }
}
