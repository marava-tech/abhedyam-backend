package com.abhedyam.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Works out how much of each due is paid: payments are replayed in the order they were recorded, and each one
 * fills the due with the earliest date that already existed at that moment. Pure, so it is recomputed on every read.
 */
final class DueAllocator {
    private DueAllocator() {}

    record Due(UUID id, BigDecimal amount, LocalDate dueDate, Instant createdAt) {}

    /** [dueId] = the due the owner said it pays (filled first), or null. */
    record Paid(BigDecimal amount, Instant recordedAt, UUID dueId) {
        Paid(BigDecimal amount, Instant recordedAt) { this(amount, recordedAt, null); }
    }

    enum Status { OPEN, PARTLY_PAID, PAID, OVERDUE }

    /** Paid amount per due id (every due is present, 0 when nothing reached it). */
    static Map<UUID, BigDecimal> allocate(List<Due> dues, List<Paid> payments) {
        List<Due> order = new ArrayList<>(dues);
        order.sort(Comparator.comparing(Due::dueDate).thenComparing(Due::createdAt));
        Map<UUID, BigDecimal> paid = new HashMap<>();
        order.forEach(d -> paid.put(d.id(), BigDecimal.ZERO));

        List<Paid> byTime = new ArrayList<>(payments);
        byTime.sort(Comparator.comparing(Paid::recordedAt));
        for (Paid p : byTime) {
            BigDecimal left = p.amount();
            List<Due> targets = order;
            if (p.dueId() != null) { // the due it was meant for first, then the usual order
                targets = new ArrayList<>(order);
                targets.sort(Comparator.comparing((Due d) -> !d.id().equals(p.dueId())));
            }
            for (Due d : targets) {
                if (left.signum() <= 0) break;
                if (d.createdAt().isAfter(p.recordedAt())) continue;
                BigDecimal room = d.amount().subtract(paid.get(d.id()));
                if (room.signum() <= 0) continue;
                BigDecimal take = left.min(room);
                paid.merge(d.id(), take, BigDecimal::add);
                left = left.subtract(take);
            }
        }
        return paid;
    }

    static Status status(BigDecimal amount, BigDecimal paid, LocalDate dueDate, LocalDate today) {
        if (paid.compareTo(amount) >= 0) return Status.PAID;
        if (dueDate.isBefore(today)) return Status.OVERDUE;
        return paid.signum() > 0 ? Status.PARTLY_PAID : Status.OPEN;
    }
}
