package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayHabitTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant T0 = Instant.parse("2026-01-01T05:00:00Z"); // 10:30 IST
    private static Instant day(int n) { return T0.plusSeconds(n * 86400L); }
    private static PayHabit.Sale sale(int dayN, String amt) { return new PayHabit.Sale(day(dayN), new BigDecimal(amt)); }
    private static PayHabit.Paid paid(int dayN, String amt) { return new PayHabit.Paid(day(dayN), new BigDecimal(amt), false); }

    @Test
    void medianDaysOfUdharPaidBackOldestFirst() {
        var r = PayHabit.of(
            List.of(sale(0, "100"), sale(1, "200"), sale(10, "300")),
            List.of(paid(5, "100"), paid(21, "200"), paid(40, "300")), IST);
        // cleared after 5, 20 and 30 days
        assertEquals(20, r.usualDays());
        assertEquals(3, r.cleared());
        assertEquals(0, new BigDecimal("500").compareTo(r.mostOwed()));
    }

    @Test
    void cashSalesAndAdvancesAreNotUdhar() {
        var r = PayHabit.of(
            List.of(sale(0, "100"), sale(3, "50"), sale(9, "70")),
            List.of(paid(0, "100"), paid(2, "120")), IST); // day 0 paid on the spot; day 2 advance covers both later sales
        assertNull(r.usualDays());
        assertEquals(0, r.cleared());
    }

    @Test
    void writeOffClearsButNeverCountsAsPaid() {
        var r = PayHabit.of(
            List.of(sale(0, "100"), sale(1, "100"), sale(2, "100"), sale(3, "500")),
            List.of(paid(4, "100"), paid(5, "100"), new PayHabit.Paid(day(60), new BigDecimal("600"), true)), IST);
        assertEquals(2, r.cleared());
        assertNull(r.usualDays(), "two paid back is not enough history");
        assertEquals(0, new BigDecimal("600").compareTo(r.writtenOff()));
        assertEquals(LocalDate.of(2026, 3, 2), r.lastWriteOff());
    }

    @Test
    void usesOnlyTheLastTenAndPartPaymentsAddUp() {
        var sales = new java.util.ArrayList<PayHabit.Sale>();
        var pays = new java.util.ArrayList<PayHabit.Paid>();
        for (int i = 0; i < 5; i++) { sales.add(sale(i * 100, "100")); pays.add(paid(i * 100 + 90, "100")); } // old: 90 days
        for (int i = 0; i < 10; i++) { int s = 500 + i * 10; sales.add(sale(s, "100")); pays.add(paid(s + 2, "40")); pays.add(paid(s + 3, "60")); } // recent: 3 days
        var r = PayHabit.of(sales, pays, IST);
        assertEquals(3, r.usualDays());
        assertEquals(15, r.cleared());
    }
}
