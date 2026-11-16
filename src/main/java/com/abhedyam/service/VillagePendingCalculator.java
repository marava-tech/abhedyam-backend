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

    /** Per village key: customers who still owe and have an active due whose date has passed. */
    static Map<String, Long> overdueByVillage(java.util.List<com.abhedyam.model.PaymentDue> activeDues,
                                              java.time.LocalDate today,
                                              Map<UUID, BigDecimal> salesByCustomer,
                                              Map<UUID, BigDecimal> paidByCustomer,
                                              Map<UUID, String> villageByCustomer) {
        java.util.Set<UUID> late = new java.util.HashSet<>();
        for (var d : activeDues) {
            if (d.getDueDate() != null && d.getDueDate().isBefore(today)) late.add(d.getCustomerId());
        }
        Map<String, Long> result = new HashMap<>();
        for (UUID c : late) {
            String village = villageByCustomer.get(c);
            BigDecimal pending = salesByCustomer.getOrDefault(c, BigDecimal.ZERO).subtract(paidByCustomer.getOrDefault(c, BigDecimal.ZERO));
            if (village != null && pending.signum() > 0) result.merge(key(village), 1L, Long::sum);
        }
        return result;
    }
}
