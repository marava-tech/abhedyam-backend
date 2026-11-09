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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({AccountService.class, AccountExportService.class, com.abhedyam.config.JpaConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
class AccountServiceTest {
    @Autowired AccountService service;
    @Autowired AccountExportService exportService;
    @MockBean GmailSender gmailSender;
    @MockBean com.abhedyam.service.interfaces.IFileUploadService files;
    @MockBean(name = "virtualThreadExecutor") Executor executor;
    @Autowired OwnerRepository owners;
    @Autowired CustomerRepository customers;
    @Autowired SaleItemRepository sales;
    @Autowired PaymentRepository payments;
    @Autowired OwnerSettingsRepository settings;
    @Autowired EntityManager em;

    private static String url(String name) {
        return "https://res.cloudinary.com/demo/image/upload/v1/abhedyam/" + name + ".jpg";
    }

    private UUID seed(String name) {
        Owner o = new Owner();
        o.setName(name); o.setImageUrl(url(name)); o.setType(com.abhedyam.model.enums.UserType.BUSINESS); o.setPhone("9" + Math.abs(name.hashCode() % 900000000 + 100000000)); o.setBusinessName(name + " store");
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
        verify(files).deleteFiles(java.util.Set.of(url("Alpha")));
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

    private OwnerSettings settingsOf(UUID id) { return settings.findByOwnerId(id).orElseThrow(); }

    private void askedHoursAgo(UUID id, long hours) {
        OwnerSettings st = settingsOf(id);
        st.setDeletionRequestedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(hours)));
        settings.save(st);
    }

    @Test
    void deletionIsScheduledNotImmediateAndCanBeCancelled() {
        UUID id = seed("Eps");
        java.time.Instant at = service.requestDeletion(id);
        assertTrue(java.time.Duration.between(java.time.Instant.now().plus(java.time.Duration.ofHours(48)), at).abs().toMinutes() < 1);
        assertTrue(owners.findById(id).orElseThrow().getIsActive());
        // asking again must not push the date out
        assertTrue(java.time.Duration.between(at, service.requestDeletion(id)).abs().toSeconds() < 1);
        assertTrue(service.deletionScheduledFor(id).isPresent());
        service.cancelDeletion(id);
        assertTrue(service.deletionScheduledFor(id).isEmpty());
    }

    @Test
    void jobEmailsDataAt12HoursOnceThenDisablesAt48AndFreesTheEmailAndPhones() throws Exception {
        UUID id = seed("Zeta");
        Owner o = owners.findById(id).orElseThrow();
        o.setEmail("zeta@example.com"); o.setFirebaseUid("uid-zeta"); o.setPhoneNormalized("+919000000001");
        owners.save(o);
        Customer c = customers.findByOwnerId(id).get(0);
        c.setPhoneNormalized("+919000000002");
        customers.save(c);
        AccountDeletionJob job = new AccountDeletionJob(settings, owners, service, exportService);

        service.requestDeletion(id);
        job.run(java.time.Instant.now());                       // just asked: nothing yet
        verify(gmailSender, times(0)).sendGmailWithAttachment(any(), any(), any(), any(), any(), any());

        askedHoursAgo(id, 13);
        job.run(java.time.Instant.now());
        job.run(java.time.Instant.now());                       // second tick must not send again
        verify(gmailSender, times(1)).sendGmailWithAttachment(any(), any(), any(), any(), any(), any());
        assertNotNull(settingsOf(id).getDeletionExportSentAt());
        assertTrue(owners.findById(id).orElseThrow().getIsActive());

        askedHoursAgo(id, 49);
        job.run(java.time.Instant.now());

        Owner gone = owners.findById(id).orElseThrow();         // disabled, not deleted
        assertFalse(gone.getIsActive());
        assertNotNull(gone.getDeletedAt());
        assertEquals("zeta@example.com", gone.getFormerEmail());
        assertNull(gone.getEmail()); assertNull(gone.getFirebaseUid()); assertNull(gone.getPhoneNormalized());
        assertTrue(settings.findByOwnerId(id).isEmpty());
        assertFalse(owners.existsByIdAndIsActiveTrue(id));
        Customer kept = customers.findByOwnerId(id).get(0);     // ledger and customer rows stay
        assertFalse(kept.getIsActive());
        assertNull(kept.getPhoneNormalized());
        assertNotNull(kept.getPhone());
        assertEquals(1, sales.findByOwnerId(id).size());

        // Disabled accounts are erased 90 days later, not before.
        Owner d = owners.findById(id).orElseThrow();
        d.setDeletedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(89)));
        owners.save(d);
        job.run(java.time.Instant.now());
        assertTrue(owners.findById(id).isPresent());
        d.setDeletedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(91)));
        owners.save(d);
        job.run(java.time.Instant.now());
        assertTrue(owners.findById(id).isEmpty());
        assertTrue(customers.findByOwnerId(id).isEmpty());
        assertTrue(sales.findByOwnerId(id).isEmpty());

        // The same Gmail and the same customer phone work for a brand new account.
        Owner again = new Owner();
        again.setName("Zeta"); again.setType(com.abhedyam.model.enums.UserType.BUSINESS); again.setBusinessName("Zeta again");
        again.setEmail("zeta@example.com"); again.setFirebaseUid("uid-zeta"); again.setPhoneNormalized("+919000000001");
        again = owners.saveAndFlush(again);
        Customer same = new Customer();
        same.setName("Same customer"); same.setType(com.abhedyam.model.enums.UserType.CUSTOMER);
        same.setOwnerId(again.getId()); same.setPhoneNormalized("+919000000002");
        customers.saveAndFlush(same);
    }
}
