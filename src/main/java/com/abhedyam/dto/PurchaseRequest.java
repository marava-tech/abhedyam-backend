package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Stock bought in one go. totalPaid is split across lines without a unitCost by quantity × selling price.")
public class PurchaseRequest {
    @Data
    public static class Line {
        @Schema(description = "An existing item; or send newItem") private UUID itemId;
        @Schema(description = "Creates the item: name and price required") private ItemRequest newItem;
        @Schema(example = "25") private BigDecimal quantity;
        @Schema(description = "Exact buying price per unit, optional") private BigDecimal unitCost;
    }

    @Schema(example = "Ramesh traders") private String supplier;
    @Schema(description = "Default today") private LocalDate boughtOn;
    @Schema(example = "12000") private BigDecimal totalPaid;
    private String note;
    private List<Line> items;
}
