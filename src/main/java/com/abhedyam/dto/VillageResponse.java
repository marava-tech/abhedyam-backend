package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Village response with customer count")
public class VillageResponse {
    @Schema(description = "Village name", example = "Koramangala")
    private String village;
    
    @Schema(description = "Number of customers in this village", example = "15")
    private Long customerCount;

    @Schema(description = "Amount still to collect from customers in this village (overpaid customers count as 0). Null on endpoints that do not compute it.", example = "8500.00", nullable = true)
    private java.math.BigDecimal pendingAmount;

    @Schema(description = "Customers here who owe anything (same rule as pendingAmount). Null where not computed.", example = "6", nullable = true)
    private Long owingCount;

    @Schema(description = "Customers here who still owe and missed a promised date. Null where not computed.", example = "2", nullable = true)
    private Long overdueCount;

    @Schema(description = "Earliest planned collection visit here that is not done yet (may be in the past = missed). Null when none.", example = "2026-11-18", nullable = true)
    private java.time.LocalDate nextVisit;

    public VillageResponse(String village, Long customerCount) {
        this(village, customerCount, null, null, null, null);
    }
}

