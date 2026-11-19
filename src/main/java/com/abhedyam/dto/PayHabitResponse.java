package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/** How a customer pays back udhar (service `PayHabit`). Shown to the shop only, never on the khata link. */
public record PayHabitResponse(
    @Schema(description = "Usual days to pay back a sale on udhar (median of the last 10); null until 3 are paid back") Integer usualDays,
    @Schema(description = "Sales on udhar paid back so far") int cleared,
    @Schema(description = "Highest balance this customer ever had") BigDecimal mostOwed,
    @Schema(description = "Total written off") BigDecimal writtenOff,
    @Schema(description = "Day of the latest write-off, or null") LocalDate lastWriteOff) {}
