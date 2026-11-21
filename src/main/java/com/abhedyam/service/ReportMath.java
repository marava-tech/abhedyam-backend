package com.abhedyam.service;

import com.abhedyam.dto.ReportResponse;
import com.abhedyam.model.enums.PaymentMedium;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sums report rows into a {@link ReportResponse}, by the owner's calendar day. Kept apart from the queries so the
 * day boundaries and the cash/UPI split are tested without a database.
 */
final class ReportMath {
    private ReportMath() {}

    /**
     * @param sales    rows of [createdAt Instant, line total BigDecimal, transactionId String, customerId UUID]
     * @param payments rows of [timestamp Instant, amount BigDecimal, medium PaymentMedium, writeOff Boolean]
     */
    static ReportResponse build(LocalDate from, LocalDate to, ZoneId zone, List<Object[]> sales, List<Object[]> payments) {
        Map<LocalDate, BigDecimal> soldByDay = new LinkedHashMap<>();
        Map<LocalDate, Set<String>> billsByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> collectedByDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            soldByDay.put(d, BigDecimal.ZERO);
            billsByDay.put(d, new HashSet<>());
            collectedByDay.put(d, BigDecimal.ZERO);
        }

        BigDecimal sold = BigDecimal.ZERO;
        Set<String> bills = new HashSet<>();
        Set<Object> customers = new HashSet<>();
        for (Object[] r : sales) {
            LocalDate day = ((Instant) r[0]).atZone(zone).toLocalDate();
            if (!soldByDay.containsKey(day)) continue; // the query is by instant; a row just outside the day range
            BigDecimal amount = r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1];
            String bill = r[2] == null ? "line-" + r[0] : (String) r[2];
            sold = sold.add(amount);
            bills.add(bill);
            if (r[3] != null) customers.add(r[3]);
            soldByDay.merge(day, amount, BigDecimal::add);
            billsByDay.get(day).add(bill);
        }

        BigDecimal collected = BigDecimal.ZERO, cash = BigDecimal.ZERO, upi = BigDecimal.ZERO, writtenOff = BigDecimal.ZERO;
        for (Object[] r : payments) {
            LocalDate day = ((Instant) r[0]).atZone(zone).toLocalDate();
            if (!collectedByDay.containsKey(day)) continue;
            BigDecimal amount = r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1];
            if (Boolean.TRUE.equals(r[3])) {
                writtenOff = writtenOff.add(amount); // forgiven, not money in hand
                continue;
            }
            collected = collected.add(amount);
            collectedByDay.merge(day, amount, BigDecimal::add);
            PaymentMedium medium = (PaymentMedium) r[2];
            if (medium == null || medium == PaymentMedium.CASH || medium == PaymentMedium.OFFLINE) cash = cash.add(amount);
            else upi = upi.add(amount); // UPI, PhonePe, Paytm, in-app
        }

        List<ReportResponse.Day> days = new ArrayList<>();
        for (LocalDate d : soldByDay.keySet()) {
            days.add(new ReportResponse.Day(d, soldByDay.get(d), billsByDay.get(d).size(), collectedByDay.get(d)));
        }
        return new ReportResponse(from, to, sold, bills.size(), customers.size(), collected, cash, upi, writtenOff, days);
    }
}
