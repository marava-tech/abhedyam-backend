package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** On create amount and dueDate are required; on update only what is sent changes. */
@Data
public class PaymentDueRequest {
    @Schema(description = "Promised amount", example = "5000")
    private BigDecimal amount;

    @Schema(description = "Date the customer promised to pay by", example = "2026-11-12")
    private LocalDate dueDate;

    @Schema(description = "Optional note, up to 200 characters", example = "After harvest")
    private String note;
}
