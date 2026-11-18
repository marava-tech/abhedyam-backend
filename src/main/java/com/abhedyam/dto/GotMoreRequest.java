package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Got more: stock that came in for one or more items")
public class GotMoreRequest {
    private List<Line> lines;
    @Schema(example = "From Ramesh traders") private String note;

    @Data
    public static class Line {
        private UUID itemId;
        @Schema(example = "20") private BigDecimal quantity;
    }
}
