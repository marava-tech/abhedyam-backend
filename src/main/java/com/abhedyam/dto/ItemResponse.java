package com.abhedyam.dto;

import com.abhedyam.model.enums.StockUnit;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ItemResponse {
    public enum Status { OK, LOW, OUT, UNTRACKED }

    private UUID id;
    private String name;
    private BigDecimal price;
    private BigDecimal costPrice;
    private StockUnit unit;
    private boolean trackStock;
    private BigDecimal stock;
    private BigDecimal lowStockLevel;
    private Status status;
    private BigDecimal stockValue;
    private String imageUrl;
    private Instant updatedAt;
    private BigDecimal soldLast30;
    private Instant lastSoldAt;
    private Integer daysLeft;
    private boolean notSelling;
}
