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

    public VillageResponse(String village, Long customerCount) {
        this(village, customerCount, null);
    }
}

