package com.abhedyam.service;

import com.abhedyam.model.*;
import com.abhedyam.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.boot.test.mock.mockito.MockBean;
import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({AccountService.class, AccountExportService.class, com.abhedyam.config.JpaConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
class AccountServiceTest {
    @Autowired AccountService service;
    @Autowired AccountExportService exportService;
    @MockBean GmailSender gmailSender;
    @MockBean(name = "virtualThreadExecutor") Executor executor;
    @Autowired OwnerRepository owners;
    @Autowired CustomerRepository customers;
    @Autowired SaleItemRepository sales;
    @Autowired PaymentRepository payments;
    @Autowired EntityManager em;

    private UUID seed(String name) {
        Owner o = new Owner();
        o.setName(name); o.setType(com.abhedyam.model.enums.UserType.BUSINESS); o.setPhone("9" + Math.abs(name.hashCode() % 900000000 + 100000000)); o.setBusinessName(name + " store");
        o = owners.save(o);
        Customer c = new Customer();
        c.setName("C of " + name); c.setType(com.abhedyam.model.enums.UserType.CUSTOMER); c.setPhone("8" + Math.abs(name.hashCode() % 900000000 + 100000000)); c.setOwnerId(o.getId());
        c = customers.save(c);
        SaleItem s = new SaleItem();
        s.setOwnerId(o.getId()); s.setCustomerId(c.getId()); s.setProductId(UUID.randomUUID());
        s.setPrice(BigDecimal.TEN); s.setQuantity(BigDecimal.ONE); s.setRemainingAmount(BigDecimal.TEN); s.setTransactionId("t");
        sales.save(s);
        return o.getId();
    }

    @Test
    void deleteRemovesOnlyThatOwner() {
        UUID gone = seed("Alpha"), kept = seed("Beta");
        service.deleteAccount(gone);
        assertTrue(owners.findById(gone).isEmpty());
        assertTrue(customers.findByOwnerId(gone).isEmpty());
        assertTrue(sales.findByOwnerId(gone).isEmpty());
        assertTrue(owners.findById(kept).isPresent());
        assertEquals(1, customers.findByOwnerId(kept).size());
        assertEquals(1, sales.findByOwnerId(kept).size());
    }

    @Test
    void exportIsRateLimitedToOncePerWeekAndNeedsEmail() throws Exception {
        UUID id = seed("Delta");
        assertEquals("EMAIL_REQUIRED", assertThrows(com.abhedyam.exception.BusinessException.class, () -> exportService.requestExport(id)).getErrorCode());
        Owner o = owners.findById(id).get();
        o.setEmail("delta@example.com");
        owners.save(o);
        assertEquals("d***a@example.com", exportService.requestExport(id));
        assertEquals("RATE_LIMITED", assertThrows(com.abhedyam.exception.BusinessException.class, () -> exportService.requestExport(id)).getErrorCode());
        byte[] zip = exportService.zip(id);
        var names = new java.util.ArrayList<String>();
        try (var z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (var e = z.getNextEntry(); e != null; e = z.getNextEntry()) names.add(e.getName());
        }
        assertEquals(java.util.List.of("customers.csv", "sales.csv", "payments.csv"), names);
    }

    @Test
    void exportCsvHasHeaderAndRows() {
        UUID id = seed("Gamma");
        String csv = service.exportCsv(id, "sales");
        assertTrue(csv.startsWith("Date,Customer,Item"));
        assertTrue(csv.contains("C of Gamma"));
        assertThrows(com.abhedyam.exception.BusinessException.class, () -> service.exportCsv(id, "nope"));
    }
}
