package com.abhedyam.dto;

import com.abhedyam.model.enums.StockUnit;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class PurchaseResponse {
    @Data
    @AllArgsConstructor
    public static class Line {
        private UUID itemId;
        private String name;
        private StockUnit unit;
        private BigDecimal quantity;
        private BigDecimal left;
        private BigDecimal unitCost;
        private BigDecimal price;
    }

    private UUID id;
    private String supplier;
    private LocalDate boughtOn;
    private BigDecimal totalPaid;
    private String note;
    private List<Line> items;
    private int leftPercent;
    private BigDecimal costLeft;
    private BigDecimal soldValue;
    private boolean paidBack;
    private BigDecimal profit;
    private boolean finished;
    private boolean canDelete;
    private Instant createdAt;
}
