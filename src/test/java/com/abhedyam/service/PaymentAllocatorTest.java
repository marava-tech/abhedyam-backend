package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAllocatorTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void fillsOldestFirstSplitsAcrossItemsAndSkipsPaidOnes() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), paid = UUID.randomUUID();
        List<PaymentAllocator.Open> open = List.of(
                new PaymentAllocator.Open(paid, d("0")),
                new PaymentAllocator.Open(a, d("300")),
                new PaymentAllocator.Open(b, d("500.50")),
                new PaymentAllocator.Open(c, d("200")));

        var pieces = PaymentAllocator.allocate(open, d("700.25"));

        assertEquals(2, pieces.size());
        assertEquals(a, pieces.get(0).saleItemId());
        assertEquals(0, d("300").compareTo(pieces.get(0).amount()));
        assertEquals(b, pieces.get(1).saleItemId());
        assertEquals(0, d("400.25").compareTo(pieces.get(1).amount()));
        assertEquals(0, d("1000.50").compareTo(PaymentAllocator.total(open)));
    }

    @Test
    void allocatedPiecesAlwaysSumToTheAmountWhenWithinTotal() {
        var open = List.of(new PaymentAllocator.Open(UUID.randomUUID(), d("10.10")),
                new PaymentAllocator.Open(UUID.randomUUID(), d("0.05")));
        var sum = PaymentAllocator.allocate(open, d("10.15")).stream()
                .map(PaymentAllocator.Piece::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, d("10.15").compareTo(sum));
    }
}
