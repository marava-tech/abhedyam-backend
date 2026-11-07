package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class UpcomingDuesResponse {
    /** Unpaid part of the dues from today up to the window end (overdue ones not included). */
    private BigDecimal upcomingAmount;
    private long overdueCount;
    private BigDecimal overdueAmount;
    /** Overdue first (oldest first), then upcoming by date. Paid dues are left out. */
    private List<OwnerDueResponse> dues;
}
