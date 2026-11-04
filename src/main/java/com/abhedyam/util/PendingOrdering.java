package com.abhedyam.util;

import com.abhedyam.model.Customer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Filter and order customers by what they owe. Input order is kept when byPending is false. */
public final class PendingOrdering {
    private PendingOrdering() {}

    public static List<Customer> apply(List<Customer> in, Map<UUID, BigDecimal> pending, boolean byPending, boolean descending, boolean owesOnly) {
        List<Customer> rows = new ArrayList<>();
        for (Customer c : in) {
            if (!owesOnly || pending.getOrDefault(c.getId(), BigDecimal.ZERO).signum() > 0) rows.add(c);
        }
        if (byPending) {
            Comparator<Customer> amount = Comparator.comparing(c -> pending.getOrDefault(c.getId(), BigDecimal.ZERO));
            if (descending) amount = amount.reversed();
            rows.sort(amount
                .thenComparing(c -> c.getName() == null ? "" : c.getName().toLowerCase())
                .thenComparing(c -> String.valueOf(c.getId())));
        }
        return rows;
    }
}
