package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class UpcomingDuesResponse {
    /** Unpaid part of the dues from today up to the window end (missed ones not included). */
    private BigDecimal upcomingAmount;
    private long missedCount;
    private BigDecimal missedAmount;
    /** Missed first (oldest first), then upcoming by date. Paid dues are left out. */
    private List<OwnerDueResponse> dues;
}
