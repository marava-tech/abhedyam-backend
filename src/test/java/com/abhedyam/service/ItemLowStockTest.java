package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import com.abhedyam.dto.ItemResponse;
import com.abhedyam.model.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** "Running low" means selling and near the warn level; an item that is not selling is never low (not on To buy). */
class ItemLowStockTest {
    private final Instant now = Instant.parse("2026-11-11T07:00:00Z");

    private Product item(Instant created) {
        Product p = new Product();
        p.setName("Fan");
        p.setPrice(new BigDecimal("1000"));
        p.setTrackStock(true);
        p.setLowStockLevel(new BigDecimal("5"));
        p.setCreatedAt(created);
        return p;
    }

    @Test
    void sellingAndBelowLevel_IsLow() {
        var sales = new ItemService.Sales(new BigDecimal("6"), now.minus(Duration.ofDays(2)));
        var r = ItemService.toResponse(item(now.minus(Duration.ofDays(200))), new BigDecimal("3"), sales, now);
        assertEquals(ItemResponse.Status.LOW, r.getStatus());
    }

    @Test
    void neverSoldOldItemBelowLevel_IsNotLow() {
        var r = ItemService.toResponse(item(now.minus(Duration.ofDays(200))), new BigDecimal("3"), ItemService.Sales.NONE, now);
        assertEquals(ItemResponse.Status.OK, r.getStatus());
        assertTrue(r.isNotSelling());
    }

    @Test
    void newItemNotSoldYetBelowLevel_IsStillLow() {
        var r = ItemService.toResponse(item(now.minus(Duration.ofDays(5))), new BigDecimal("3"), ItemService.Sales.NONE, now);
        assertEquals(ItemResponse.Status.LOW, r.getStatus());
    }
}
