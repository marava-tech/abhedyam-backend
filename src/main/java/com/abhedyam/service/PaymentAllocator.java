package com.abhedyam.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Spreads a payment over open sale items in the given order (caller sorts oldest first). */
final class PaymentAllocator {
    private PaymentAllocator() {}

    record Open(UUID saleItemId, BigDecimal remaining) {}

    record Piece(UUID saleItemId, BigDecimal amount) {}

    /** Items with no balance are skipped. Any amount beyond total balance is simply not allocated. */
    static List<Piece> allocate(List<Open> open, BigDecimal amount) {
        List<Piece> pieces = new ArrayList<>();
        BigDecimal left = amount;
        for (Open o : open) {
            if (left.signum() <= 0) break;
            if (o.remaining().signum() <= 0) continue;
            BigDecimal take = left.min(o.remaining());
            pieces.add(new Piece(o.saleItemId(), take));
            left = left.subtract(take);
        }
        return pieces;
    }

    static BigDecimal total(List<Open> open) {
        return open.stream().map(Open::remaining).filter(r -> r.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
