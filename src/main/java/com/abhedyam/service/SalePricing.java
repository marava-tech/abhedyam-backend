package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.exception.BusinessException;

import java.math.BigDecimal;

/** Money rules for creating a sale: line totals in whole paise and the paid-at-sale checks. */
final class SalePricing {
    private SalePricing() {}

    static BigDecimal quantityOf(BigDecimal requested) {
        return requested == null ? BigDecimal.ONE : requested;
    }

    /** price x quantity, which must come to a whole number of paise so totals never drift. */
    static BigDecimal lineTotal(BigDecimal price, BigDecimal quantity) {
        BigDecimal total = price.multiply(quantity);
        if (total.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(ErrorCodes.INVALID_QUANTITY,
                    "Price x quantity must be a whole number of paise. Adjust the quantity or the price.");
        }
        return total.setScale(2);
    }

    static void checkPaid(BigDecimal saleTotal, BigDecimal paid, boolean walkIn) {
        BigDecimal paidNow = paid == null ? BigDecimal.ZERO : paid;
        if (paidNow.compareTo(saleTotal) > 0) {
            throw new BusinessException(ErrorCodes.PAYMENT_EXCEEDS_DUE,
                    String.format("Paid amount (₹%s) cannot exceed the sale total (₹%s).", paidNow, saleTotal));
        }
        if (walkIn && paidNow.compareTo(saleTotal) != 0) {
            throw new BusinessException(ErrorCodes.WALK_IN_MUST_BE_PAID,
                    "A walk-in sale must be paid in full. Choose a customer to sell on credit.");
        }
    }
}
