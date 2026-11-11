package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Collection cycle: WEEKLY with day 1-7 (Mon-Sun) or MONTHLY with day 1-31, plus the amount collected each time")
public record CollectionCycleDto(
        @Schema(example = "WEEKLY") String kind,
        @Schema(example = "1") Integer day,
        @Schema(example = "500") BigDecimal amount) {
}
