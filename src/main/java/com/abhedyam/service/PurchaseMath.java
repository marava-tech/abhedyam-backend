package com.abhedyam.service;

import com.abhedyam.model.StockMovement;
import com.abhedyam.model.enums.StockMovementType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The two calculations behind purchases (specs/stock-purchases.md); pure, so they are tested on their own. */
final class PurchaseMath {

    private PurchaseMath() {
    }

    /** One purchase line for the cost split: [given] is the owner's exact unit cost, or null to estimate it. */
    record Line(BigDecimal quantity, BigDecimal price, BigDecimal given) {
    }

    /**
     * Unit cost per line. Given costs stay; the rest of [totalPaid] is shared by relative sales value
     * (quantity × selling price), or by quantity when every remaining price is 0.
     * Throws IllegalArgumentException when the given costs alone exceed the total.
     */
    static List<BigDecimal> unitCosts(List<Line> lines, BigDecimal totalPaid) {
        BigDecimal given = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        BigDecimal quantities = BigDecimal.ZERO;
        for (Line l : lines) {
            if (l.given() != null) {
                given = given.add(l.given().multiply(l.quantity()));
            } else {
                weights = weights.add(l.quantity().multiply(l.price()));
                quantities = quantities.add(l.quantity());
            }
        }
        BigDecimal rest = totalPaid.subtract(given);
        if (rest.signum() < 0) throw new IllegalArgumentException("over");
        boolean byQuantity = weights.signum() == 0;
        List<BigDecimal> out = new ArrayList<>();
        for (Line l : lines) {
            if (l.given() != null) {
                out.add(l.given().setScale(4, RoundingMode.HALF_UP));
            } else if (quantities.signum() == 0) {
                out.add(BigDecimal.ZERO.setScale(4));
            } else {
                BigDecimal share = byQuantity
                        ? rest.multiply(l.quantity()).divide(quantities, 10, RoundingMode.HALF_UP)
                        : rest.multiply(l.quantity()).multiply(l.price()).divide(weights, 10, RoundingMode.HALF_UP);
                out.add(share.divide(l.quantity(), 4, RoundingMode.HALF_UP));
            }
        }
        return out;
    }

    /**
     * What is left of each purchase line for one item, oldest stock used first. [movements] are that item's
     * movements in time order; movements of [deletedPurchases] are skipped. Key: the purchase id of each RECEIVED bucket.
     */
    static Map<UUID, BigDecimal> leftByPurchase(List<StockMovement> movements, Set<UUID> deletedPurchases) {
        List<StockMovement> buckets = new ArrayList<>();
        BigDecimal used = BigDecimal.ZERO;
        for (StockMovement m : movements) {
            if (m.getPurchaseId() != null && deletedPurchases.contains(m.getPurchaseId())) continue;
            BigDecimal q = m.getQuantity();
            if (m.getType() == StockMovementType.SALE_CANCELLED) {
                used = used.subtract(q);
            } else if (q.signum() > 0) {
                buckets.add(m);
            } else {
                used = used.add(q.negate());
            }
        }
        if (used.signum() < 0) used = BigDecimal.ZERO;
        Map<UUID, BigDecimal> left = new HashMap<>();
        for (StockMovement b : buckets) {
            BigDecimal take = used.min(b.getQuantity());
            used = used.subtract(take);
            if (b.getPurchaseId() != null) left.merge(b.getPurchaseId(), b.getQuantity().subtract(take), BigDecimal::add);
        }
        return left;
    }
}
