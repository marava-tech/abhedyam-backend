package com.abhedyam.service;

import com.abhedyam.model.StockMovement;
import com.abhedyam.model.enums.StockMovementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PurchaseMathTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static StockMovement m(StockMovementType t, String q, UUID purchase) {
        StockMovement x = new StockMovement();
        x.setType(t);
        x.setQuantity(d(q));
        x.setPurchaseId(purchase);
        return x;
    }

    @Test
    void totalOnlyIsSplitByRelativeSalesValue() {
        // 10 rice @ ₹60 = 600, 5 oil @ ₹120 = 600 → equal value → ₹1,000 split 500/500
        List<BigDecimal> c = PurchaseMath.unitCosts(List.of(
                new PurchaseMath.Line(d("10"), d("60"), null),
                new PurchaseMath.Line(d("5"), d("120"), null)), d("1000"));
        assertEquals(0, d("50").compareTo(c.get(0)));
        assertEquals(0, d("100").compareTo(c.get(1)));
    }

    @Test
    void givenCostsStayAndTheRestIsShared() {
        List<BigDecimal> c = PurchaseMath.unitCosts(List.of(
                new PurchaseMath.Line(d("10"), d("60"), d("45")),
                new PurchaseMath.Line(d("4"), d("100"), null)), d("850"));
        assertEquals(0, d("45").compareTo(c.get(0)));
        assertEquals(0, d("100").compareTo(c.get(1)), "850 − 450 = 400 over 4");
    }

    @Test
    void givenCostsAboveTheTotalAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PurchaseMath.unitCosts(List.of(
                new PurchaseMath.Line(d("10"), d("60"), d("50"))), d("100")));
    }

    @Test
    void zeroPricesSplitByQuantity() {
        List<BigDecimal> c = PurchaseMath.unitCosts(List.of(
                new PurchaseMath.Line(d("3"), d("0"), null),
                new PurchaseMath.Line(d("1"), d("0"), null)), d("400"));
        assertEquals(0, d("100").compareTo(c.get(0)));
        assertEquals(0, d("100").compareTo(c.get(1)));
    }

    @Test
    void oldestStockIsUsedFirst() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        List<StockMovement> ms = List.of(
                m(StockMovementType.OPENING, "5", null),
                m(StockMovementType.RECEIVED, "10", p1),
                m(StockMovementType.SALE, "-8", null),
                m(StockMovementType.RECEIVED, "10", p2),
                m(StockMovementType.SALE, "-4", null),
                m(StockMovementType.SALE_CANCELLED, "2", null));
        // used = 8 + 4 − 2 = 10: opening 5 gone, purchase 1 has 5 left, purchase 2 untouched
        Map<UUID, BigDecimal> left = PurchaseMath.leftByPurchase(ms, Set.of());
        assertEquals(0, d("5").compareTo(left.get(p1)));
        assertEquals(0, d("10").compareTo(left.get(p2)));
    }

    @Test
    void deletedPurchasesAreIgnored() {
        UUID gone = UUID.randomUUID(), kept = UUID.randomUUID();
        List<StockMovement> ms = List.of(
                m(StockMovementType.RECEIVED, "10", gone),
                m(StockMovementType.REMOVED, "-10", gone),
                m(StockMovementType.RECEIVED, "6", kept),
                m(StockMovementType.SALE, "-2", null));
        Map<UUID, BigDecimal> left = PurchaseMath.leftByPurchase(ms, Set.of(gone));
        assertEquals(0, d("4").compareTo(left.get(kept)));
        assertFalse(left.containsKey(gone));
    }
}
