package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ItemListResponse {
    @Data
    @AllArgsConstructor
    public static class Summary {
        private int total;
        private int tracked;
        private int low;
        private int out;
        private BigDecimal stockValue;
        private int missingCostPrice;
        private BigDecimal potentialProfit;
        private int notSelling;
        private BigDecimal notSellingValue;
    }

    private Summary summary;
    private PageResponse<ItemResponse> items;
}
