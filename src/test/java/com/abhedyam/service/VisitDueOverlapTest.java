package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.model.CollectionVisit;
import com.abhedyam.model.Customer;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.PaymentDue;
import com.abhedyam.repository.*;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.service.interfaces.IOwnerSettingsService;
import com.abhedyam.util.SecurityUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Upcoming collections: a customer with a promise date is counted by that due, not again in their village's visit. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitDueOverlapTest {
    @Mock PaymentDueRepository dueRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock CustomerRepository customerRepository;
    @Mock LocationDetailsRepository locations;
    @Mock CollectionVisitRepository visitRepository;
    @Mock SaleItemRepository saleItems;
    @Mock ReminderRepository reminders;
    @Mock INotificationSettingsService notificationSettings;
    @Mock IOwnerSettingsService ownerSettings;
    @InjectMocks PaymentDueService service;

    final UUID owner = UUID.randomUUID(), ravi = UUID.randomUUID(), sita = UUID.randomUUID();
    MockedStatic<SecurityUtil> sec;

    @BeforeEach
    void setUp() {
        sec = mockStatic(SecurityUtil.class);
        sec.when(SecurityUtil::getCurrentUserId).thenReturn(owner);
        when(notificationSettings.forOwner(owner)).thenReturn(new NotificationSettings());
        when(ownerSettings.villageMode(owner)).thenReturn(true);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        PaymentDue due = new PaymentDue();
        due.setId(UUID.randomUUID());
        due.setOwnerId(owner);
        due.setCustomerId(ravi);
        due.setAmount(new BigDecimal("1000"));
        due.setDueDate(today.plusDays(2));
        due.setCreatedAt(Instant.now());
        when(dueRepository.findByOwnerIdAndIsActiveTrue(owner)).thenReturn(List.of(due));
        Customer r = new Customer();
        r.setId(ravi);
        r.setName("Ravi");
        when(customerRepository.findAllById(any())).thenReturn(List.of(r));
        when(paymentRepository.findByCustomerIdInAndOwnerId(any(), eq(owner))).thenReturn(List.of());

        CollectionVisit visit = new CollectionVisit();
        visit.setId(UUID.randomUUID());
        visit.setOwnerId(owner);
        visit.setVillage("Ravipadu");
        visit.setVisitDate(today.plusDays(1));
        when(visitRepository.findByOwnerIdAndIsActiveTrueAndDoneAtIsNull(owner)).thenReturn(List.of(visit));
        when(locations.findCustomerVillagesByOwnerId(owner)).thenReturn(List.of(
                new Object[]{ravi.toString(), "Ravipadu"}, new Object[]{sita.toString(), "Ravipadu"}));
        when(saleItems.sumTotalsByCustomer(owner)).thenReturn(List.of(
                new Object[]{ravi.toString(), "3000"}, new Object[]{sita.toString(), "2000"}));
        when(paymentRepository.sumSuccessfulByCustomer(owner)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() { sec.close(); }

    @Test
    void customerWithDueLeftOutOfTheVisit() {
        var up = service.upcoming(owner, 7);
        assertEquals(1, up.getDues().size());
        var visit = up.getVisits().get(0);
        assertEquals(1, visit.getOwingCount()); // only Sita
        assertEquals(0, new BigDecimal("2000").compareTo(visit.getOwingAmount()));
    }

    @Test
    void visitOnItsOwnStillCountsEveryone() {
        var visit = service.ownerVisits(owner).get(0);
        assertEquals(2, visit.getOwingCount());
        assertEquals(0, new BigDecimal("5000").compareTo(visit.getOwingAmount()));
    }
}
