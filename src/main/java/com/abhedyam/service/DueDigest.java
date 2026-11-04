package com.abhedyam.service;

import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.SaleItemStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
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
                        List<SaleItem> saleItems, Instant now) {
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
        Instant oldest = saleItems.stream()
            .filter(i -> owing.contains(i.getCustomerId()) && i.getStatus() != SaleItemStatus.FULLY_PAID && i.getCreatedAt() != null)
            .map(SaleItem::getCreatedAt)
            .min(Instant::compareTo)
            .orElse(now);
        return new DueDigest(owing.size(), total.setScale(0, RoundingMode.HALF_UP), Math.max(0, Duration.between(oldest, now).toDays()));
    }

    String text() {
        String amount = indian(total.longValue());
        return customers + (customers == 1 ? " customer owes ₹" : " customers owe ₹") + amount
            + " · oldest " + oldestDays + (oldestDays == 1 ? " day" : " days");
    }

    /** 124000 -> "1,24,000" (Java's en-IN formatter groups in thousands, which Indian owners do not expect). */
    static String indian(long n) {
        String s = Long.toString(Math.abs(n));
        if (s.length() <= 3) return (n < 0 ? "-" : "") + s;
        String rest = s.substring(0, s.length() - 3).replaceAll("(\\d)(?=(\\d{2})+$)", "$1,");
        return (n < 0 ? "-" : "") + rest + "," + s.substring(s.length() - 3);
    }
}
