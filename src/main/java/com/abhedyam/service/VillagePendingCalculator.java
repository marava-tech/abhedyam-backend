package com.abhedyam.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Pending per village = sum over customers of max(0, sales - paid).
 * Overpaid (advance) customers contribute 0 so they never offset others' dues.
 */
final class VillagePendingCalculator {
    private VillagePendingCalculator() {}

    static String key(String village) {
        return village == null ? "" : village.trim().toLowerCase(Locale.ROOT);
    }

    static Map<String, BigDecimal> pendingByVillage(Map<UUID, BigDecimal> salesByCustomer,
                                                    Map<UUID, BigDecimal> paidByCustomer,
                                                    Map<UUID, String> villageByCustomer) {
        Map<String, BigDecimal> result = new HashMap<>();
        villageByCustomer.forEach((customerId, village) -> {
            BigDecimal pending = salesByCustomer.getOrDefault(customerId, BigDecimal.ZERO)
                    .subtract(paidByCustomer.getOrDefault(customerId, BigDecimal.ZERO));
            if (pending.signum() > 0) {
                result.merge(key(village), pending, BigDecimal::add);
            }
        });
        return result;
    }

    record Owing(long customers, BigDecimal amount) {}

    /** Per village key: how many customers owe and how much (same max(0, sales - paid) rule). */
    static Map<String, Owing> owingByVillage(Map<UUID, BigDecimal> salesByCustomer,
                                             Map<UUID, BigDecimal> paidByCustomer,
                                             Map<UUID, String> villageByCustomer) {
        Map<String, Owing> result = new HashMap<>();
        villageByCustomer.forEach((customerId, village) -> {
            BigDecimal pending = salesByCustomer.getOrDefault(customerId, BigDecimal.ZERO)
                    .subtract(paidByCustomer.getOrDefault(customerId, BigDecimal.ZERO));
            if (pending.signum() > 0) {
                result.merge(key(village), new Owing(1, pending),
                        (a, b) -> new Owing(a.customers() + b.customers(), a.amount().add(b.amount())));
            }
        });
        return result;
    }
}
