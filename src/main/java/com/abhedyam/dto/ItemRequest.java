package com.abhedyam.dto;

import com.abhedyam.model.enums.StockUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Create or edit an item. On edit only the fields sent change.")
public class ItemRequest {
    @Schema(example = "Basmati rice") private String name;
    @Schema(description = "Selling price", example = "120") private BigDecimal price;
    @Schema(description = "Buying price; 0 clears it", example = "95") private BigDecimal costPrice;
    @Schema(description = "PCS (default), KG, G, L, ML, BOX, DOZEN") private StockUnit unit;
    @Schema(description = "Count stock for this item") private Boolean trackStock;
    @Schema(description = "Stock right now; turns tracking on", example = "25.5") private BigDecimal openingStock;
    @Schema(description = "Show as low at or below this; 0 clears it", example = "5") private BigDecimal lowStockLevel;
    private String imageUrl;
}
