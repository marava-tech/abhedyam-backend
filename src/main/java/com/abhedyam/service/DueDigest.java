package com.abhedyam.service;


import com.abhedyam.util.Txt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "7 customers owe ₹1,24,000 · oldest 45 days". Same rule as the Customers list: a customer owes
 * max(0, sales - paid), so an overpaid customer never offsets someone else's due.
 */
record DueDigest(int customers, BigDecimal total, long oldestDays) {

    /** @return null when nobody owes anything (no digest is sent). */
    static DueDigest of(Map<UUID, BigDecimal> salesByCustomer, Map<UUID, BigDecimal> paidByCustomer,
                        Map<UUID, Instant> oldestUnpaidByCustomer, Instant now) {
        Set<UUID> owing = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var e : salesByCustomer.entrySet()) {
            BigDecimal pending = e.getValue().subtract(paidByCustomer.getOrDefault(e.getKey(), BigDecimal.ZERO));
            if (pending.signum() > 0) {
                owing.add(e.getKey());
                total = total.add(pending);
            }
        }
        if (owing.isEmpty()) return null;
        Instant oldest = owing.stream()
            .map(oldestUnpaidByCustomer::get)
            .filter(java.util.Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(now);
        return new DueDigest(owing.size(), total.setScale(0, RoundingMode.HALF_UP), Math.max(0, Duration.between(oldest, now).toDays()));
    }

    Txt text() {
        return Txt.of("notif.digest.owe", customers, indian(total.longValue()), oldestDays);
    }

    /** "2 dues today ₹7,000 · 1 overdue ₹3,000", or null when no due is today or overdue. */
    static Txt duesLine(List<com.abhedyam.dto.OwnerDueResponse> dues, LocalDate today) {
        int todayCount = 0, overdueCount = 0;
        BigDecimal todayAmount = BigDecimal.ZERO, overdueAmount = BigDecimal.ZERO;
        for (var o : dues) {
            var d = o.getDue();
            if (d.getStatus().equals("OVERDUE")) { overdueCount++; overdueAmount = overdueAmount.add(d.getRemaining()); }
            else if (!d.getStatus().equals("PAID") && d.getDueDate().equals(today)) { todayCount++; todayAmount = todayAmount.add(d.getRemaining()); }
        }
        List<Txt> parts = new java.util.ArrayList<>();
        if (todayCount > 0) parts.add(Txt.of("notif.digest.duesToday", todayCount, indian(todayAmount.setScale(0, RoundingMode.HALF_UP).longValue())));
        if (overdueCount > 0) parts.add(Txt.of("notif.digest.overdue", overdueCount, indian(overdueAmount.setScale(0, RoundingMode.HALF_UP).longValue())));
        return parts.isEmpty() ? null : Txt.of("notif.join", parts);
    }

    /** "Buy soon: Oil (out), Rice (2 kg left) +1 more", or null when no tracked item is out or low. Out first. */
    static Txt stockLine(List<com.abhedyam.dto.ItemResponse> items) {
        List<com.abhedyam.dto.ItemResponse> need = items.stream()
            .filter(i -> i.getStatus() == com.abhedyam.dto.ItemResponse.Status.OUT || i.getStatus() == com.abhedyam.dto.ItemResponse.Status.LOW)
            .sorted(java.util.Comparator.comparing((com.abhedyam.dto.ItemResponse i) -> i.getStatus() != com.abhedyam.dto.ItemResponse.Status.OUT)
                .thenComparing(com.abhedyam.dto.ItemResponse::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        if (need.isEmpty()) return null;
        List<Txt> names = new java.util.ArrayList<>();
        for (var i : need.subList(0, Math.min(2, need.size()))) {
            names.add(i.getStatus() == com.abhedyam.dto.ItemResponse.Status.OUT ? Txt.of("notif.stock.out", i.getName())
                : Txt.of("notif.stock.left", i.getName(), i.getStock().stripTrailingZeros().toPlainString(), Txt.of("unit." + i.getUnit().name())));
        }
        Txt shown = names.size() == 1 ? names.get(0) : Txt.of("notif.stock.two", names.get(0), names.get(1));
        return Txt.of("notif.stock.buySoon", need.size() > 2 ? Txt.of("notif.stock.more", shown, need.size() - 2) : shown);
    }

    /** 124000 -> "1,24,000" (Java's en-IN formatter groups in thousands, which Indian owners do not expect). */
    static String indian(long n) {
        String s = Long.toString(Math.abs(n));
        if (s.length() <= 3) return (n < 0 ? "-" : "") + s;
        String rest = s.substring(0, s.length() - 3).replaceAll("(\\d)(?=(\\d{2})+$)", "$1,");
        return (n < 0 ? "-" : "") + rest + "," + s.substring(s.length() - 3);
    }
}
