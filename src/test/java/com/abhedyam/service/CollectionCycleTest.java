package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionCycleTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    // Wed 11 Nov 2026; Mon = 9 Nov, previous Mon = 2 Nov, next Mon = 16 Nov.
    private static final LocalDate WED = LocalDate.of(2026, 11, 11), MON = LocalDate.of(2026, 11, 9);
    private static final LocalDate LONG_AGO = LocalDate.of(2026, 1, 1);

    private static CollectionCycle.Result weekly(List<CollectionCycle.Paid> paid, LocalDate today) {
        return CollectionCycle.of(CollectionCycle.WEEKLY, 1, d("500"), LONG_AGO, d("10000"), paid, today);
    }

    @Test
    void missedMondayIsOverdue() {
        var r = weekly(List.of(), WED);
        assertEquals(MON, r.date());
        assertTrue(r.overdue());
        assertEquals(0, d("500").compareTo(r.remaining()));
    }

    @Test
    void paidThisCycleShowsNextMonday() {
        var r = weekly(List.of(new CollectionCycle.Paid(MON, d("500"))), WED);
        assertEquals(LocalDate.of(2026, 11, 16), r.date());
        assertFalse(r.overdue());
    }

    @Test
    void paymentOnPreviousCycleDayDoesNotCount() {
        var r = weekly(List.of(new CollectionCycle.Paid(LocalDate.of(2026, 11, 2), d("500"))), WED);
        assertTrue(r.overdue());
    }

    @Test
    void earlyPaymentBeforeTheDayCounts() {
        var r = weekly(List.of(new CollectionCycle.Paid(LocalDate.of(2026, 11, 7), d("200"))), MON);
        assertEquals(MON, r.date());
        assertFalse(r.overdue()); // today
        assertEquals(0, d("300").compareTo(r.remaining()));
    }

    @Test
    void cycleDaysBeforeItWasSetAreNotMissed() {
        var r = CollectionCycle.of(CollectionCycle.WEEKLY, 1, d("500"), WED, d("10000"), List.of(), WED);
        assertEquals(LocalDate.of(2026, 11, 16), r.date());
        assertFalse(r.overdue());
    }

    @Test
    void amountIsCappedAtBalanceAndNothingOwedMeansNoRow() {
        assertEquals(0, d("120").compareTo(
                CollectionCycle.of(CollectionCycle.WEEKLY, 1, d("500"), LONG_AGO, d("120"), List.of(), WED).expected()));
        assertNull(CollectionCycle.of(CollectionCycle.WEEKLY, 1, d("500"), LONG_AGO, d("0"), List.of(), WED));
    }

    @Test
    void monthlyDayPastMonthEndFallsOnLastDay() {
        assertEquals(LocalDate.of(2026, 10, 31), CollectionCycle.onOrBefore(CollectionCycle.MONTHLY, 31, WED));
        assertEquals(LocalDate.of(2026, 11, 30), CollectionCycle.after(CollectionCycle.MONTHLY, 31, WED));
        assertEquals(LocalDate.of(2027, 2, 28), CollectionCycle.after(CollectionCycle.MONTHLY, 30, LocalDate.of(2027, 1, 30)));
        assertEquals(LocalDate.of(2026, 12, 5), CollectionCycle.after(CollectionCycle.MONTHLY, 5, LocalDate.of(2026, 11, 5)));
    }

    @Test
    void validation() {
        assertTrue(CollectionCycle.valid("WEEKLY", 7));
        assertFalse(CollectionCycle.valid("WEEKLY", 8));
        assertTrue(CollectionCycle.valid("MONTHLY", 31));
        assertFalse(CollectionCycle.valid("DAILY", 1));
        assertFalse(CollectionCycle.valid("MONTHLY", null));
    }
}
