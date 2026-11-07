package com.abhedyam.dto;

import com.abhedyam.model.enums.StockRemovalReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "RECEIVED / REMOVED: quantity > 0. COUNT: quantity = what you counted (>= 0).")
public class StockChangeRequest {
    public enum Type { RECEIVED, REMOVED, COUNT }

    private Type type;
    @Schema(example = "10") private BigDecimal quantity;
    @Schema(description = "Only for REMOVED") private StockRemovalReason reason;
    @Schema(example = "From Ramesh traders") private String note;
}
