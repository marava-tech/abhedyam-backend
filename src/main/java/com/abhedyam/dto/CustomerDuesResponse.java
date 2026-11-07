package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class CustomerDuesResponse {
    /** What the customer owes now. */
    private BigDecimal balance;
    /** Unpaid part of all dues. */
    private BigDecimal planned;
    /** Part of the balance with no due date: max(0, balance - planned). */
    private BigDecimal unplanned;
    private long missedCount;
    /** Earliest due that is not fully paid, or null. */
    private PaymentDueResponse next;
    /** Sorted by due date. */
    private List<PaymentDueResponse> dues;
}
