package com.abhedyam;

import com.abhedyam.model.Customer;
import com.abhedyam.util.PendingOrdering;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingOrderingTest {
    private Customer c(String name) {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setName(name);
        return c;
    }

    @Test
    void sortsByOwedAndFiltersOwes() {
        Customer a = c("Asha"), b = c("Bala"), z = c("Zed"), neg = c("Neg"), none = c("None");
        Map<UUID, BigDecimal> p = new HashMap<>();
        p.put(a.getId(), new BigDecimal("500"));
        p.put(b.getId(), new BigDecimal("500"));
        p.put(z.getId(), new BigDecimal("900"));
        p.put(neg.getId(), new BigDecimal("-20")); // overpaid
        List<Customer> in = List.of(a, b, z, neg, none);

        assertEquals(List.of("Zed", "Asha", "Bala", "None", "Neg"),
                PendingOrdering.apply(in, p, true, true, false).stream().map(Customer::getName).toList());
        assertEquals(List.of("Asha", "Bala", "Zed"),
                PendingOrdering.apply(in, p, true, false, true).stream().map(Customer::getName).toList());
        // owesOnly alone keeps the input (village) order
        assertEquals(List.of("Asha", "Bala", "Zed"),
                PendingOrdering.apply(in, p, false, true, true).stream().map(Customer::getName).toList());
    }
}
