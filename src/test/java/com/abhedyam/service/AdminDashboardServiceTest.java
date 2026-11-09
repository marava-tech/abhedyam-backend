package com.abhedyam.service;

import com.abhedyam.dto.AdminOverview;
import com.abhedyam.dto.AdminShopDetail;
import com.abhedyam.dto.AdminShopRow;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.*;
import com.abhedyam.model.enums.ReferralStatus;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.service.interfaces.IBillingService;
import com.abhedyam.service.interfaces.IGrowthAdminService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({AdminDashboardService.class, com.abhedyam.config.JpaConfig.class})
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
class AdminDashboardServiceTest {
    @Autowired AdminDashboardService service;
    @Autowired EntityManager em;
    @MockBean IBillingService billing;
    @MockBean IGrowthAdminService growth;

    static final Instant NOW = Instant.now();
    UUID trial, paying, readOnly, deleting, closed;

    private UUID owner(String name, Instant accessUntil, String product, boolean active) {
        Owner o = new Owner();
        o.setName(name); o.setBusinessName(name + " Stores"); o.setType(UserType.BUSINESS);
        o.setPhone("9" + Math.abs(name.hashCode() % 900000000 + 100000000)); o.setEmail(name.toLowerCase() + "@example.com");
        o.setAccessUntil(accessUntil); o.setPlayProductId(product); o.setIsActive(active);
        if (product != null) o.setFirstPaidAt(NOW.minus(1, ChronoUnit.DAYS));
        em.persist(o);
        return o.getId();
    }

    @BeforeEach
    void seed() {
        when(growth.trialCalls()).thenReturn(List.of());
        trial = owner("Ravi", NOW.plus(2, ChronoUnit.DAYS), null, true);
        paying = owner("Sita", NOW.plus(300, ChronoUnit.DAYS), "abhedyam_plan:yearly", true);
        readOnly = owner("Gopal", NOW.minus(5, ChronoUnit.DAYS), null, true);
        deleting = owner("Lata", NOW.plus(20, ChronoUnit.DAYS), null, true);
        closed = owner("Old", NOW.minus(50, ChronoUnit.DAYS), null, false);

        OwnerSettings s = new OwnerSettings();
        s.setOwnerId(deleting); s.setDeletionRequestedAt(NOW);
        em.persist(s);
        OwnerSettings t = new OwnerSettings();
        t.setOwnerId(trial); t.setVillageModeEnabled(true); t.setOtherFlags(Map.of("business", Map.of("villages", true)));
        em.persist(t);

        Customer c = new Customer();
        c.setName("Prava"); c.setType(UserType.CUSTOMER); c.setPhone("8123456789"); c.setOwnerId(trial);
        em.persist(c);
        SaleItem sale = new SaleItem();
        sale.setOwnerId(trial); sale.setCustomerId(c.getId()); sale.setProductId(UUID.randomUUID());
        sale.setPrice(BigDecimal.TEN); sale.setQuantity(BigDecimal.ONE); sale.setRemainingAmount(BigDecimal.TEN); sale.setTransactionId("t1");
        em.persist(sale);

        OwnerSession phone = new OwnerSession();
        phone.setOwnerId(trial); phone.setDeviceName("Vivo V2250"); phone.setLastSeenAt(NOW);
        em.persist(phone);

        Referral cash = new Referral();
        cash.setReferrerOwnerId(paying); cash.setRefereeOwnerId(trial); cash.setStatus(ReferralStatus.PAID); cash.setRewardCash(true);
        em.persist(cash);
        Owner t2 = em.find(Owner.class, trial);
        t2.setReferredBy(paying.toString());
        em.flush();
    }

    @Test
    void statusRules() {
        assertEquals("CLOSED", AdminDashboardService.status(false, true, "p", NOW.plusSeconds(60), NOW));
        assertEquals("DELETING", AdminDashboardService.status(true, true, "p", NOW.plusSeconds(60), NOW));
        assertEquals("PAYING", AdminDashboardService.status(true, false, "p", NOW.minusSeconds(60), NOW));
        assertEquals("TRIAL", AdminDashboardService.status(true, false, null, NOW.plusSeconds(60), NOW));
        assertEquals("READ_ONLY", AdminDashboardService.status(true, false, null, null, NOW));
        assertEquals(2, AdminDashboardService.daysLeft(NOW.plus(36, ChronoUnit.HOURS), NOW));
        assertEquals(0, AdminDashboardService.daysLeft(NOW.minusSeconds(1), NOW));
    }

    @Test
    void overviewCountsEveryStatusAndWhatIsOwed() {
        AdminOverview o = service.overview();
        assertEquals(new AdminOverview.Shops(5, 1, 1, 1, 1, 1), o.shops());
        assertEquals(Map.of("abhedyam_plan:yearly", 1L), o.payingByProduct());
        assertEquals(99, o.foundingLeft());
        assertEquals(5, o.signupsToday());
        assertEquals(30, o.signupsByDay().size());
        assertEquals(1, o.activeShops7d());
        assertEquals(1, o.trialsEnding3d());
        assertEquals(new AdminOverview.Funnel(5, 1, 1, 1), o.funnel30d());
        assertEquals(200, o.owed().referralCashRupees());
    }

    @Test
    void shopsFilterSearchAndSource() {
        assertEquals(List.of(readOnly), service.shops(null, "READ_ONLY", null, 0, 25).getContent().stream().map(AdminShopRow::id).toList());
        AdminShopRow ravi = service.shops("ravi stores", null, null, 0, 25).getContent().get(0);
        assertEquals(trial, ravi.id());
        assertEquals(1, ravi.customers());
        assertEquals(1, ravi.sales30d());
        assertNotNull(ravi.lastSeenAt());
        assertEquals(new AdminShopRow.Source("REFERRAL", paying, "Sita Stores"), ravi.source());
        assertEquals(trial, service.shops(null, null, "lastSeen", 0, 25).getContent().get(0).id());
        assertEquals(2, service.shops(null, null, null, 1, 3).getContent().size());
        assertThrows(BusinessException.class, () -> service.shops(null, "NOPE", null, 0, 25));
    }

    @Test
    void shopDetail() {
        AdminShopDetail d = service.shop(trial);
        assertEquals("TRIAL", d.shop().status());
        assertEquals(1, d.sales());
        assertEquals(Map.of("villages", true), d.setup());
        assertEquals(Boolean.TRUE, d.villageMode());
        assertEquals("Vivo V2250", d.devices().get(0).name());
        AdminShopDetail sita = service.shop(paying);
        assertEquals(1, sita.referredFriends().size());
        assertTrue(sita.referredFriends().get(0).paid());
    }

    @Test
    void referralCashIsMarkedPaidOnce() {
        var row = service.referrals().get(0);
        assertEquals(200, row.cashRupees());
        assertNull(row.cashPaidAt());
        service.markReferralCashPaid(row.id());
        em.flush();
        Instant paidAt = service.referrals().get(0).cashPaidAt();
        assertNotNull(paidAt);
        service.markReferralCashPaid(row.id());
        assertEquals(paidAt, service.referrals().get(0).cashPaidAt());
        assertEquals(0, service.overview().owed().referralCashRupees());

        Referral days = new Referral();
        days.setReferrerOwnerId(readOnly); days.setRefereeOwnerId(deleting);
        em.persist(days);
        assertEquals("NO_CASH_REWARD", assertThrows(BusinessException.class, () -> service.markReferralCashPaid(days.getId())).getErrorCode());
    }
}
