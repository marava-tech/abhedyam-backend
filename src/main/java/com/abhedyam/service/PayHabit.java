package com.abhedyam.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * How a customer pays back udhar, from their sales and payments only (no setup by the owner).
 * Sales are cleared oldest first by later payments; a sale cleared on the day it was made was a cash sale, not udhar,
 * so it doesn't count. A write-off clears sales too, but those never count as paid. Pure, so it is recomputed on read.
 */
final class PayHabit {
    private PayHabit() {}

    record Sale(Instant at, BigDecimal amount) {}

    record Paid(Instant at, BigDecimal amount, boolean writeOff) {}

    /**
     * [usualDays] median days to pay back the last [RECENT] udhar sales (null until there are [MIN_CLEARED]),
     * [cleared] udhar sales paid back in all, [mostOwed] the highest balance they ever had,
     * [writtenOff] total written off, [lastWriteOff] its latest day (or null).
     */
    record Result(Integer usualDays, int cleared, BigDecimal mostOwed, BigDecimal writtenOff, LocalDate lastWriteOff) {}

    static final int MIN_CLEARED = 3;
    static final int RECENT = 10;

    static Result of(List<Sale> sales, List<Paid> payments, ZoneId zone) {
        // Sales before payments at the same instant: a sale paid on the spot is cleared the same day.
        record Event(Instant at, int order, Sale sale, Paid paid) {}
        List<Event> events = new ArrayList<>();
        sales.forEach(s -> events.add(new Event(s.at(), 0, s, null)));
        payments.forEach(p -> events.add(new Event(p.at(), 1, null, p)));
        events.sort(Comparator.comparing(Event::at).thenComparingInt(Event::order));

        Deque<BigDecimal[]> open = new ArrayDeque<>(); // [remaining, sale day as epoch day]
        List<Long> days = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO, most = BigDecimal.ZERO, advance = BigDecimal.ZERO, writtenOff = BigDecimal.ZERO;
        LocalDate lastWriteOff = null;
        for (Event e : events) {
            LocalDate day = e.at().atZone(zone).toLocalDate();
            if (e.sale() != null) {
                BigDecimal amount = e.sale().amount();
                balance = balance.add(amount);
                most = most.max(balance);
                BigDecimal fromAdvance = advance.min(amount); // paid in advance: never udhar
                advance = advance.subtract(fromAdvance);
                if (amount.compareTo(fromAdvance) > 0) open.add(new BigDecimal[]{amount.subtract(fromAdvance), BigDecimal.valueOf(day.toEpochDay())});
                continue;
            }
            Paid p = e.paid();
            balance = balance.subtract(p.amount());
            if (p.writeOff()) {
                writtenOff = writtenOff.add(p.amount());
                lastWriteOff = day;
            }
            BigDecimal left = p.amount();
            while (left.signum() > 0 && !open.isEmpty()) {
                BigDecimal[] s = open.peek();
                BigDecimal take = left.min(s[0]);
                s[0] = s[0].subtract(take);
                left = left.subtract(take);
                if (s[0].signum() == 0) {
                    open.poll();
                    long d = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(s[1].longValue()), day);
                    if (!p.writeOff() && d > 0) days.add(d);
                }
            }
            advance = advance.add(left);
        }
        List<Long> recent = new ArrayList<>(days.subList(Math.max(0, days.size() - RECENT), days.size()));
        Collections.sort(recent);
        Integer usual = days.size() < MIN_CLEARED ? null : recent.get(recent.size() / 2).intValue();
        return new Result(usual, days.size(), most, writtenOff, lastWriteOff);
    }
}
