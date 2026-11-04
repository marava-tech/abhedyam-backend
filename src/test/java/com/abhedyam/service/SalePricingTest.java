package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SalePricingTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static String codeOf(Runnable r) {
        return assertThrows(BusinessException.class, r::run).getErrorCode();
    }

    @Test
    void quantityDefaultsToOne() {
        assertEquals(0, d("1").compareTo(SalePricing.quantityOf(null)));
        assertEquals(0, d("2.5").compareTo(SalePricing.quantityOf(d("2.5"))));
    }

    @Test
    void lineTotalIsPriceTimesQuantityInWholePaise() {
        assertEquals(d("2400.00"), SalePricing.lineTotal(d("1200"), d("2")));
        assertEquals(d("100.00"), SalePricing.lineTotal(d("40.00"), d("2.5")));
        assertEquals(d("5.25"), SalePricing.lineTotal(d("10.50"), d("0.5")));
    }

    @Test
    void lineTotalWithFractionOfPaiseIsRejected() {
        assertEquals(ErrorCodes.INVALID_QUANTITY, codeOf(() -> SalePricing.lineTotal(d("10.50"), d("2.333"))));
        assertEquals(ErrorCodes.INVALID_QUANTITY, codeOf(() -> SalePricing.lineTotal(d("10.50"), d("0.25"))));
    }

    @Test
    void paidCannotExceedTotal() {
        assertEquals(ErrorCodes.PAYMENT_EXCEEDS_DUE,
                codeOf(() -> SalePricing.checkPaid(d("100"), d("100.01"), false)));
        assertDoesNotThrow(() -> SalePricing.checkPaid(d("100"), d("100"), false));
        assertDoesNotThrow(() -> SalePricing.checkPaid(d("100"), null, false));
    }

    @Test
    void walkInMustBePaidInFull() {
        assertEquals(ErrorCodes.WALK_IN_MUST_BE_PAID, codeOf(() -> SalePricing.checkPaid(d("100"), d("40"), true)));
        assertEquals(ErrorCodes.WALK_IN_MUST_BE_PAID, codeOf(() -> SalePricing.checkPaid(d("100"), null, true)));
        assertDoesNotThrow(() -> SalePricing.checkPaid(d("100"), d("100"), true));
    }
}
