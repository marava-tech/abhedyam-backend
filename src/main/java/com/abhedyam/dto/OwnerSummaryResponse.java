package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Owner summary statistics")
public class OwnerSummaryResponse {
    @Schema(description = "Total pending amount across all customers", example = "50000.00")
    private BigDecimal totalPendingAmount;
    
    @Schema(description = "Total number of customers", example = "150")
    private Long totalCustomers;
    
    @Schema(description = "Number of distinct villages", example = "25")
    private Long totalVillages;
    
    @Schema(description = "Total collected amount from successful payments", example = "200000.00")
    private BigDecimal totalCollectedAmount;

    @Schema(description = "Amount collected via successful payments in the current calendar month (Asia/Kolkata)", example = "12000.00")
    private BigDecimal collectedThisMonth;

    @Schema(description = "Amount collected via successful payments today (Asia/Kolkata)", example = "600.00")
    private BigDecimal collectedToday;

    @Schema(description = "Number of successful payments today (Asia/Kolkata)", example = "1")
    private Long collectedTodayCount;
}
