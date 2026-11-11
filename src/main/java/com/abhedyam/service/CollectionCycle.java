package com.abhedyam.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A customer's collection cycle ("₹500 every Monday", "₹2,000 on the 5th") worked out for one day. Spec: collection-cycle.md. */
public final class CollectionCycle {
    public static final String WEEKLY = "WEEKLY", MONTHLY = "MONTHLY";

    private CollectionCycle() {}

    public record Paid(LocalDate date, BigDecimal amount) {}

    /** date = the cycle day to show; overdue = that day passed without the money. */
    public record Result(LocalDate date, BigDecimal expected, BigDecimal paid, boolean overdue) {
        public BigDecimal remaining() { return expected.subtract(paid).max(BigDecimal.ZERO); }
    }

    public static boolean valid(String kind, Integer day) {
        if (day == null) return false;
        return WEEKLY.equals(kind) ? day >= 1 && day <= 7 : MONTHLY.equals(kind) && day >= 1 && day <= 31;
    }

    /** Latest cycle day on or before [d]. Monthly days past the month's end fall on its last day. */
    static LocalDate onOrBefore(String kind, int day, LocalDate d) {
        if (WEEKLY.equals(kind)) return d.minusDays((d.getDayOfWeek().getValue() - day + 7) % 7);
        LocalDate c = inMonth(d, day);
        return c.isAfter(d) ? inMonth(d.minusMonths(1), day) : c;
    }

    /** First cycle day after [d]. */
    static LocalDate after(String kind, int day, LocalDate d) {
        if (WEEKLY.equals(kind)) return d.plusDays(7 - (d.getDayOfWeek().getValue() - day + 7) % 7);
        LocalDate c = inMonth(d, day);
        return c.isAfter(d) ? c : inMonth(d.plusMonths(1), day);
    }

    private static LocalDate inMonth(LocalDate d, int day) {
        return d.withDayOfMonth(Math.min(day, d.lengthOfMonth()));
    }

    /** Null when nothing is owed. [payments] = successful payments by local date. */
    public static Result of(String kind, int day, BigDecimal amount, LocalDate since, BigDecimal balance,
                            List<Paid> payments, LocalDate today) {
        if (balance == null || balance.signum() <= 0) return null;
        BigDecimal expected = amount.min(balance);
        LocalDate last = onOrBefore(kind, day, today);
        LocalDate prev = onOrBefore(kind, day, last.minusDays(1));
        BigDecimal collected = payments.stream().filter(p -> p.date().isAfter(prev))
                .map(Paid::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if ((since != null && last.isBefore(since)) || collected.compareTo(expected) >= 0) {
            return new Result(after(kind, day, today), expected, BigDecimal.ZERO, false);
        }
        // ponytail: only the latest missed cycle shows; add a backlog sum if owners ask for it
        return new Result(last, expected, collected, last.isBefore(today));
    }
}
