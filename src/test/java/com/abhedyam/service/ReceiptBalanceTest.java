package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.model.Payment;
import com.abhedyam.model.SaleItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The receipt's "Pending balance" is the customer's whole balance, not just the one sale item it was made from. */
class ReceiptBalanceTest {
    private static SaleItem sale(String price, String qty) {
        SaleItem s = new SaleItem();
        s.setPrice(new BigDecimal(price));
        s.setQuantity(qty == null ? null : new BigDecimal(qty));
        return s;
    }

    private static Payment paid(String amount, PaymentStatus status) {
        Payment p = new Payment();
        p.setAmount(new BigDecimal(amount));
        p.setStatus(status);
        return p;
    }

    @Test
    void twoSalesTwoPayments() {
        // ₹2,350 + ₹2,350 + ₹500 sold, ₹500 paid (the failed ₹1,000 does not count): owes ₹4,700.
        var sales = List.of(sale("2350", "1"), sale("1175", "2"), sale("500", null));
        var payments = List.of(paid("500", PaymentStatus.SUCCESS), paid("1000", PaymentStatus.FAILED));
        assertEquals(0, new BigDecimal("4700").compareTo(InvoiceReceiptService.balance(sales, payments)));
    }
}
