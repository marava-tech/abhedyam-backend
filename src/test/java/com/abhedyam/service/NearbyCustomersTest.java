package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.NearbyCustomer;
import com.abhedyam.dto.NearbyCustomersResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.PaymentDue;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.PaymentDueRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.util.SecurityUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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

/** Nearby customers (spec nearby-customers.md): who owes, closest first, no village-name filter, the money-line facts. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NearbyCustomersTest {
    @Mock CustomerRepository customerRepository;
    @Mock LocationDetailsRepository locations;
    @Mock SaleItemRepository saleItems;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentDueRepository dueRepository;
    @Mock INotificationSettingsService notificationSettings;
    @InjectMocks PaymentDueService service;

    final UUID owner = UUID.randomUUID();
    final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    // The owner stands at the Bengaluru test point.
    static final double LAT = 12.9716, LNG = 77.5946;
    final List<Customer> customers = new ArrayList<>();
    final List<LocationDetails> located = new ArrayList<>();
    final List<Object[]> sold = new ArrayList<>(), paid = new ArrayList<>();
    final List<PaymentDue> dues = new ArrayList<>();
    MockedStatic<SecurityUtil> sec;

    @BeforeEach
    void setUp() {
        sec = mockStatic(SecurityUtil.class);
        sec.when(SecurityUtil::getCurrentUserId).thenReturn(owner);
        when(notificationSettings.forOwner(owner)).thenReturn(new NotificationSettings()); // Asia/Kolkata
        when(customerRepository.findByOwnerId(owner)).thenReturn(customers);
        when(customerRepository.findAllById(any())).thenReturn(customers);
        when(customerRepository.findByOwnerIdAndCycleKindIsNotNull(owner)).thenReturn(List.of());
        when(locations.findCustomerLocationsByOwnerId(owner)).thenReturn(located);
        when(locations.findByUserIdIn(any())).thenReturn(List.of());
        when(saleItems.sumTotalsByCustomer(owner)).thenReturn(sold);
        when(paymentRepository.sumSuccessfulByCustomer(owner)).thenReturn(paid);
        when(paymentRepository.findByCustomerIdInAndOwnerId(any(), eq(owner))).thenReturn(List.of());
        when(dueRepository.findByOwnerIdAndIsActiveTrue(owner)).thenReturn(dues);
    }

    @AfterEach
    void tearDown() { sec.close(); }

    /** A customer owing [owes] (sold minus paid); [northMeters] north of the owner, or no location when null. */
    Customer customer(String name, int owes, Integer northMeters, String village) {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setOwnerId(owner);
        c.setName(name);
        c.setPhone("98480" + (10000 + customers.size()));
        customers.add(c);
        sold.add(new Object[] {c.getId(), BigDecimal.valueOf(owes + 100)});
        paid.add(new Object[] {c.getId(), BigDecimal.valueOf(100)});
        if (northMeters != null) {
            LocationDetails l = new LocationDetails();
            l.setUserId(c.getId());
            l.setLatitude(BigDecimal.valueOf(LAT + northMeters / 111_195.0)); // one degree of latitude ≈ 111.195 km
            l.setLongitude(BigDecimal.valueOf(LNG));
            l.setVillage(village);
            located.add(l);
        }
        return c;
    }

    void due(Customer c, int amount, LocalDate date) {
        PaymentDue d = new PaymentDue();
        d.setId(UUID.randomUUID());
        d.setOwnerId(owner);
        d.setCustomerId(c.getId());
        d.setAmount(BigDecimal.valueOf(amount));
        d.setDueDate(date);
        d.setCreatedAt(Instant.now());
        dues.add(d);
    }

    List<String> names(NearbyCustomersResponse r) {
        return r.getCustomers().stream().map(NearbyCustomer::getName).toList();
    }

    @Test
    void closestFirst_onlyWhoOwe_anyVillage_andCountsTheOnesWithoutLocation() {
        customer("Far", 500, 2_000, "Hanamkonda");
        customer("Near", 300, 50, "Warangal Rural");
        customer("Middle", 200, 400, "వరంగల్");
        customer("Paid up", 0, 10, "Bengaluru");
        customer("Gave up", 900, 20, "Bengaluru").setChasePaused(true);
        customer("Gone", 900, 30, "Bengaluru").setIsActive(false);
        customer("No location 1", 700, null, null);
        customer("No location 2", 100, null, null);
        customer("No location, paid", 0, null, null);

        NearbyCustomersResponse r = service.nearby(owner, LAT, LNG, 20);

        assertEquals(List.of("Near", "Middle", "Far"), names(r));
        assertEquals(2, r.getOwingWithoutLocation());
        NearbyCustomer near = r.getCustomers().get(0);
        assertEquals(0, BigDecimal.valueOf(300).compareTo(near.getBalance()));
        assertTrue(Math.abs(near.getDistanceMeters() - 50) <= 2, "~50 m, was " + near.getDistanceMeters());
        assertEquals("Warangal Rural", near.getVillage());
    }

    @Test
    void moneyLine_oldestOverdueDays_andNextDueFromToday() {
        Customer late = customer("Late", 1_000, 100, "A");
        due(late, 300, today.minusDays(12));
        due(late, 300, today.minusDays(3));
        due(late, 400, today.plusDays(5));
        Customer dueToday = customer("Due today", 500, 200, "A");
        due(dueToday, 500, today);
        customer("Just owes", 500, 300, "A");

        List<NearbyCustomer> rows = service.nearby(owner, LAT, LNG, 20).getCustomers();

        assertEquals(12, rows.get(0).getOverdueDays());
        assertEquals(today.plusDays(5), rows.get(0).getNextDueDate());
        assertNull(rows.get(1).getOverdueDays());
        assertEquals(today, rows.get(1).getNextDueDate());
        assertNull(rows.get(2).getOverdueDays());
        assertNull(rows.get(2).getNextDueDate());
    }

    @Test
    void limit_isClampedTo1to50() {
        for (int i = 0; i < 60; i++) customer("C" + i, 100, 10 * (i + 1), "A");
        assertEquals(3, service.nearby(owner, LAT, LNG, 3).getCustomers().size());
        assertEquals(50, service.nearby(owner, LAT, LNG, 500).getCustomers().size());
        assertEquals(1, service.nearby(owner, LAT, LNG, 0).getCustomers().size());
    }

    @Test
    void noOneWithLocation_isAnEmptyList_notAnError() {
        customer("No location", 700, null, null);
        NearbyCustomersResponse r = service.nearby(owner, LAT, LNG, 20);
        assertTrue(r.getCustomers().isEmpty());
        assertEquals(1, r.getOwingWithoutLocation());
    }

    @Test
    void otherOwner_andBadLocation_areRefused() {
        assertThrows(BusinessException.class, () -> service.nearby(UUID.randomUUID(), LAT, LNG, 20));
        assertThrows(BusinessException.class, () -> service.nearby(owner, 91, LNG, 20));
        assertThrows(BusinessException.class, () -> service.nearby(owner, Double.NaN, LNG, 20));
    }
}
