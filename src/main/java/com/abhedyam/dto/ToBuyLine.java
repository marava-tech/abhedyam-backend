package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class ToBuyLine {
    public enum Reason { OUT, LOW, RUNNING_OUT }

    private ItemResponse item;
    private Reason reason;
    private BigDecimal suggestedQuantity;
    private String lastSupplier;
    private BigDecimal lastUnitCost;
    private BigDecimal lastQuantity;
    private LocalDate lastBoughtOn;
}
