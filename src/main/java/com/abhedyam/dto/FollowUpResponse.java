package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** A "call again on…" row in Upcoming collections. */
@Data
@AllArgsConstructor
public class FollowUpResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private String village;
    private LocalDate date;
    private String note;
    /** UPCOMING or OVERDUE (date passed, not marked done). */
    private String status;
}
