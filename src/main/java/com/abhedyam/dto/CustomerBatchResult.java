package com.abhedyam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CustomerBatchResult {
    /** Rows that are fine: new customers (with their old balance) plus ones already in the shop (nothing added). */
    private int saved;
    private int newCustomers;
    /** Rows that got an old-balance sale. */
    private int withAmount;
    private BigDecimal totalOwes;
    private List<Problem> problems;

    /** row counts from 1; code: NO_NAME, BAD_PHONE, PHONE_TAKEN, DUPLICATE, BAD_AMOUNT, BAD_DATE, FAILED. */
    public record Problem(int row, String code) {
    }

    /** Every row and what happened: ADDED (new here, with its old balance), EXISTING (already in the shop: nothing
     *  added, so a balance is never counted twice) or a problem code. Lets the app group the list before saving. */
    private List<RowResult> rows;

    public record RowResult(int row, String name, BigDecimal owes, String status) {
    }
}
