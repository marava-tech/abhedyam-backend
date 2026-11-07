package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
public class CollectionVisitResponse {
    private UUID id;
    private String village;
    private LocalDate visitDate;
    private String note;
    /** UPCOMING or OVERDUE (date passed, not marked done). */
    private String status;
    /** Customers in that village who owe something now, and how much in total. */
    private long owingCount;
    private BigDecimal owingAmount;
}
