package com.abhedyam.repository;

import com.abhedyam.model.Payment;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Home's "who to chase" dates: latest sale and latest successful payment per customer. */
@DataJpaTest
@AutoConfigureTestDatabase
@Import(com.abhedyam.config.JpaConfig.class)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
class LastActivityQueriesTest {
    @Autowired SaleItemRepository sales;
    @Autowired PaymentRepository payments;

    @Test
    void latestSaleAndLatestSuccessfulPayment() {
        UUID owner = UUID.randomUUID(), customer = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        sale(owner, customer);
        sale(owner, customer);
        pay(owner, customer, now.minus(60, ChronoUnit.DAYS), PaymentStatus.SUCCESS);
        pay(owner, customer, now.minus(1, ChronoUnit.DAYS), PaymentStatus.SUCCESS);
        pay(owner, customer, now, PaymentStatus.PENDING); // not money in yet

        List<Object[]> p = payments.lastSuccessfulPaymentByCustomer(owner);
        assertEquals(1, p.size());
        assertEquals(customer.toString(), p.get(0)[0].toString());
        assertEquals(now.minus(1, ChronoUnit.DAYS), toInstant(p.get(0)[1]));

        List<Object[]> s = sales.lastSaleByCustomer(owner);
        assertEquals(1, s.size());
        assertNotNull(s.get(0)[1]);
        assertTrue(payments.lastSuccessfulPaymentByCustomer(UUID.randomUUID()).isEmpty()); // other owner sees nothing
    }

    @Test
    void todaysSuccessfulPaymentsCountAndSum() {
        UUID owner = UUID.randomUUID(), customer = UUID.randomUUID();
        Instant from = Instant.parse("2026-11-07T18:30:00Z"), to = Instant.parse("2026-11-08T18:30:00Z"); // 8 Nov in IST
        pay(owner, customer, Instant.parse("2026-11-07T18:30:00Z"), PaymentStatus.SUCCESS); // 00:00 IST: counts
        pay(owner, customer, Instant.parse("2026-11-08T10:00:00Z"), PaymentStatus.SUCCESS);
        pay(owner, customer, Instant.parse("2026-11-08T11:00:00Z"), PaymentStatus.PENDING);  // not in yet
        pay(owner, customer, Instant.parse("2026-11-07T18:29:59Z"), PaymentStatus.SUCCESS); // yesterday IST
        assertEquals(2, payments.countSuccessfulBetween(owner, from, to));
        assertEquals(0, new BigDecimal("2").compareTo(payments.sumSuccessfulBetween(owner, from, to)));
    }

    @Test
    void writeOffClosesTheBalanceButIsNeverMoneyIn() {
        UUID owner = UUID.randomUUID(), customer = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant from = now.minus(1, ChronoUnit.HOURS), to = now.plus(1, ChronoUnit.HOURS);
        pay(owner, customer, now.minus(30, ChronoUnit.DAYS), PaymentStatus.SUCCESS);   // real ₹1, 30 days ago
        Payment w = pay(owner, customer, now, PaymentStatus.SUCCESS);                 // ₹1 written off today
        w.setWriteOff(true);
        payments.save(w);

        // balance side: both rows close the due
        assertEquals(0, new BigDecimal("2").compareTo(new BigDecimal(payments.sumSuccessfulByCustomer(owner).get(0)[1].toString())));
        // money-in side: the write-off is invisible
        assertEquals(0, new BigDecimal("1").compareTo(payments.sumCollected(owner)));
        assertEquals(0, BigDecimal.ZERO.compareTo(payments.sumSuccessfulBetween(owner, from, to)));
        assertEquals(0, payments.countSuccessfulBetween(owner, from, to));
        assertEquals(now.minus(30, ChronoUnit.DAYS), toInstant(payments.lastSuccessfulPaymentByCustomer(owner).get(0)[1]));
    }

    private static Instant toInstant(Object o) {
        return o instanceof Instant i ? i : ((java.sql.Timestamp) o).toInstant();
    }

    private void sale(UUID owner, UUID customer) {
        SaleItem s = new SaleItem();
        s.setOwnerId(owner); s.setCustomerId(customer); s.setProductId(UUID.randomUUID());
        s.setPrice(BigDecimal.TEN); s.setQuantity(BigDecimal.ONE); s.setRemainingAmount(BigDecimal.TEN); s.setTransactionId("t");
        sales.save(s);
    }

    private Payment pay(UUID owner, UUID customer, Instant at, PaymentStatus status) {
        Payment p = new Payment();
        p.setOwnerId(owner); p.setCustomerId(customer); p.setAmount(BigDecimal.ONE);
        p.setMedium(PaymentMedium.CASH); p.setTimestamp(at); p.setStatus(status);
        return payments.save(p);
    }
}
