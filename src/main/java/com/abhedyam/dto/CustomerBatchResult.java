package com.abhedyam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CustomerBatchResult {
    /** Rows saved: new customers plus ones found already in the shop. */
    private int saved;
    private int newCustomers;
    /** Rows that got an old-balance sale. */
    private int withAmount;
    private BigDecimal totalOwes;
    private List<Problem> problems;

    /** row counts from 1; code: NO_NAME, BAD_PHONE, PHONE_TAKEN, DUPLICATE, BAD_AMOUNT, BAD_DATE, FAILED. */
    public record Problem(int row, String code) {
    }
}
