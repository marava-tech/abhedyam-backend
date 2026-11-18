package com.abhedyam.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

/** One row of Nearby customers (spec nearby-customers.md): who owes, how far, and what the money line says. */
@Data
@AllArgsConstructor
public class NearbyCustomer {
    private UUID customerId;
    private String name;
    private String phone;
    private String village;
    private BigDecimal balance;
    private long distanceMeters;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** Days since the oldest overdue due; null when nothing is overdue. */
    private Integer overdueDays;
    /** Earliest unpaid due that is not overdue (today counts); null when none. */
    private LocalDate nextDueDate;
}
