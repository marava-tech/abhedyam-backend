package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import com.abhedyam.dto.ItemResponse;
import com.abhedyam.model.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** "Running out": the owner's level, else a quarter of the last stock-up rounded up (at least 1). */
class ItemLowStockTest {
    private final Instant now = Instant.parse("2026-11-11T07:00:00Z");

    private Product item(BigDecimal level) {
        Product p = new Product();
        p.setName("Fan");
        p.setPrice(new BigDecimal("1000"));
        p.setTrackStock(true);
        p.setLowStockLevel(level);
        p.setCreatedAt(now.minus(Duration.ofDays(200)));
        return p;
    }

    private ItemResponse at(BigDecimal level, String stock, String lastStockUp) {
        return ItemService.toResponse(item(level), new BigDecimal(stock), ItemService.Sales.NONE,
                lastStockUp == null ? null : new BigDecimal(lastStockUp), now);
    }

    @Test
    void ownerLevelWins_EvenForAnItemThatIsNotSelling() {
        ItemResponse r = at(new BigDecimal("5"), "3", "100");
        assertEquals(ItemResponse.Status.LOW, r.getStatus());
        assertFalse(r.isLowStockAuto());
    }

    @Test
    void automaticLevelIsAQuarterOfTheLastStockUpRoundedUp() {
        ItemResponse r = at(null, "3", "10");
        assertEquals(0, new BigDecimal("3").compareTo(r.getLowStockLevel()));
        assertTrue(r.isLowStockAuto());
        assertEquals(ItemResponse.Status.LOW, r.getStatus());
        assertEquals(ItemResponse.Status.OK, at(null, "4", "10").getStatus());
    }

    @Test
    void automaticLevelIsAtLeastOne() {
        assertEquals(0, BigDecimal.ONE.compareTo(ItemService.autoLowLevel(new BigDecimal("0.5"))));
        assertEquals(ItemResponse.Status.LOW, at(null, "1", "2").getStatus());
    }

    @Test
    void noStockUpYet_NoLevel() {
        ItemResponse r = at(null, "3", null);
        assertNull(r.getLowStockLevel());
        assertFalse(r.isLowStockAuto());
        assertEquals(ItemResponse.Status.OK, r.getStatus());
    }
}
