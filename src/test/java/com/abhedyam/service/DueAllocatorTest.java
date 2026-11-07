package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DueAllocatorTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static Instant at(int hours) { return T0.plusSeconds(hours * 3600L); }
    private static final LocalDate NOV12 = LocalDate.of(2026, 11, 12), NOV20 = LocalDate.of(2026, 11, 20);

    @Test
    void earliestDateFillsFirstAndOverflowMovesOn() {
        UUID late = UUID.randomUUID(), early = UUID.randomUUID();
        var dues = List.of(new DueAllocator.Due(late, d("5000"), NOV20, T0),
                new DueAllocator.Due(early, d("3000"), NOV12, T0));

        var paid = DueAllocator.allocate(dues, List.of(new DueAllocator.Paid(d("2000"), at(1)),
                new DueAllocator.Paid(d("1500.50"), at(2))));

        assertEquals(0, d("3000").compareTo(paid.get(early)));
        assertEquals(0, d("500.50").compareTo(paid.get(late)));
    }

    @Test
    void paymentsBeforeADueExistedDoNotCountForIt() {
        UUID first = UUID.randomUUID(), added = UUID.randomUUID();
        var dues = List.of(new DueAllocator.Due(first, d("1000"), NOV20, T0),
                new DueAllocator.Due(added, d("1000"), NOV12, at(5)));

        // Paid before "added" existed: goes to "first" though "added" has the earlier date.
        var paid = DueAllocator.allocate(dues, List.of(new DueAllocator.Paid(d("800"), at(1)),
                new DueAllocator.Paid(d("500"), at(6)),
                new DueAllocator.Paid(d("400"), T0.minusSeconds(60))));

        assertEquals(0, d("800").compareTo(paid.get(first)));
        assertEquals(0, d("500").compareTo(paid.get(added)));
    }

    @Test
    void moneyBeyondAllDuesIsLeftOverAndNoDueGoesNegative() {
        UUID a = UUID.randomUUID();
        var paid = DueAllocator.allocate(List.of(new DueAllocator.Due(a, d("100"), NOV12, T0)),
                List.of(new DueAllocator.Paid(d("999"), at(1))));
        assertEquals(0, d("100").compareTo(paid.get(a)));
    }

    @Test
    void gotPaidOnALaterDueFillsThatDueFirst() {
        UUID early = UUID.randomUUID(), later = UUID.randomUUID();
        var dues = List.of(new DueAllocator.Due(early, d("3000"), NOV12, T0),
                new DueAllocator.Due(later, d("2000"), NOV20, T0));

        var paid = DueAllocator.allocate(dues, List.of(new DueAllocator.Paid(d("2500"), at(1), later)));

        assertEquals(0, d("2000").compareTo(paid.get(later)));
        assertEquals(0, d("500").compareTo(paid.get(early)));
    }

    @Test
    void status() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        assertEquals(DueAllocator.Status.PAID, DueAllocator.status(d("10"), d("10"), NOV12, today));
        assertEquals(DueAllocator.Status.OVERDUE, DueAllocator.status(d("10"), d("4"), NOV12, today));
        assertEquals(DueAllocator.Status.PARTLY_PAID, DueAllocator.status(d("10"), d("4"), NOV20, today));
        assertEquals(DueAllocator.Status.OPEN, DueAllocator.status(d("10"), d("0"), today, today));
    }
}
