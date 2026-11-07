package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
public class PaymentDueResponse {
    private UUID id;
    private BigDecimal amount;
    private BigDecimal paid;
    private BigDecimal remaining;
    private LocalDate dueDate;
    private String note;
    /** OPEN, PARTLY_PAID, PAID or MISSED */
    private String status;
    private Instant createdAt;
    /** Extra reminder lead time (2-30), null = only the day-before one. */
    private Integer earlyReminderDays;
}
