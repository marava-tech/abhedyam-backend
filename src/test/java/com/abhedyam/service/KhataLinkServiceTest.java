package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.KhataLinkResponse;
import com.abhedyam.dto.PublicKhataResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Payment;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.UPIAccount;
import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.UPIAccountRepository;
import com.abhedyam.security.UserPrincipal;
import com.abhedyam.service.interfaces.IPaymentDueService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Private customer link: owner gets/resets it, the public page shows the khata without the phone. */
@ExtendWith(MockitoExtension.class)
class KhataLinkServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private OwnerRepository ownerRepository;
    @Mock private SaleItemRepository saleItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UPIAccountRepository upiAccountRepository;
    @Mock private IPaymentDueService dueService;
    @InjectMocks private KhataLinkService service;

    private final UUID ownerId = UUID.randomUUID();
    private final Customer prava = customer(ownerId);

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private static void as(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(id, null), null, List.of()));
    }

    private static Customer customer(UUID owner) {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setOwnerId(owner);
        c.setName("Prava");
        c.setPhone("9876543210");
        return c;
    }

    private SaleItem sale(String price, String qty, Instant at) {
        SaleItem s = new SaleItem();
        s.setPrice(new BigDecimal(price));
        s.setQuantity(new BigDecimal(qty));
        s.setCreatedAt(at);
        return s;
    }

    private Payment payment(String amount, PaymentStatus status, Instant at) {
        Payment p = new Payment();
        p.setAmount(new BigDecimal(amount));
        p.setStatus(status);
        p.setMedium(PaymentMedium.values()[0]);
        p.setTimestamp(at);
        return p;
    }

    @Test
    void get_MakesTokenOnce_ThenKeepsIt() {
        as(ownerId);
        when(customerRepository.findById(prava.getId())).thenReturn(Optional.of(prava));

        KhataLinkResponse first = service.get(ownerId, prava.getId());
        KhataLinkResponse again = service.get(ownerId, prava.getId());

        assertEquals(32, first.token().length());
        assertEquals("https://duesetu.com/k/" + first.token(), first.url());
        assertEquals(first.token(), again.token());
        verify(customerRepository, times(1)).save(prava);
    }

    @Test
    void reset_GivesNewToken() {
        as(ownerId);
        prava.setLinkToken("old");
        when(customerRepository.findById(prava.getId())).thenReturn(Optional.of(prava));

        KhataLinkResponse r = service.reset(ownerId, prava.getId());

        assertNotEquals("old", r.token());
        assertEquals(r.token(), prava.getLinkToken());
    }

    @Test
    void get_OtherShopsCustomer_Refused() {
        as(ownerId);
        Customer other = customer(UUID.randomUUID());
        when(customerRepository.findById(other.getId())).thenReturn(Optional.of(other));

        assertThrows(BusinessException.class, () -> service.get(ownerId, other.getId()));
    }

    @Test
    void get_NotTheOwner_Refused() {
        as(UUID.randomUUID());
        assertThrows(BusinessException.class, () -> service.get(ownerId, prava.getId()));
    }

    @Test
    void view_UnknownToken_NotFound() {
        when(customerRepository.findByLinkToken("nope")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.view("nope"));
    }

    @Test
    void view_DeletedCustomer_NotFound() {
        prava.setDeletedAt(Instant.now());
        when(customerRepository.findByLinkToken("t")).thenReturn(Optional.of(prava));
        assertThrows(ResourceNotFoundException.class, () -> service.view("t"));
    }

    @Test
    void view_ShowsBalanceUpiAndEntries_NeverPhoneOrPending() {
        Owner owner = new Owner();
        owner.setId(ownerId);
        owner.setBusinessName("Sri Lakshmi Stores");
        owner.setPhone("9000000000");
        UPIAccount upi = new UPIAccount();
        upi.setVpa("shop@ybl");
        Instant t0 = Instant.parse("2026-11-01T10:00:00Z");
        when(customerRepository.findByLinkToken("t")).thenReturn(Optional.of(prava));
        when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(saleItemRepository.findByCustomerIdAndOwnerId(prava.getId(), ownerId))
                .thenReturn(List.of(sale("500", "2", t0), sale("200", "1", t0.plusSeconds(60))));
        when(paymentRepository.findByCustomerIdAndOwnerId(prava.getId(), ownerId)).thenReturn(List.of(
                payment("300", PaymentStatus.SUCCESS, t0.plusSeconds(120)),
                payment("999", PaymentStatus.PENDING, t0.plusSeconds(180))));
        when(upiAccountRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(upi));
        when(dueService.listForLink(eq(ownerId), eq(prava.getId()), any()))
                .thenReturn(new CustomerDuesResponse(new BigDecimal("900"), BigDecimal.ZERO, new BigDecimal("900"), 0, null, List.of()));

        PublicKhataResponse r = service.view("t");

        assertEquals(0, new BigDecimal("900").compareTo(r.balance())); // 1000 + 200 - 300; pending 999 ignored
        verify(dueService).listForLink(eq(ownerId), eq(prava.getId()), argThat(b -> b.compareTo(new BigDecimal("900")) == 0));
        assertEquals("Sri Lakshmi Stores", r.shopName());
        assertEquals("Prava", r.customerName());
        assertEquals("shop@ybl", r.upiId());
        assertNull(r.nextDue());
        assertEquals(List.of("PAYMENT", "SALE", "SALE"), r.entries().stream().map(PublicKhataResponse.Entry::kind).toList());
        assertFalse(r.toString().contains("9876543210"));
    }

    @Test
    void entries_NewestFirst_CappedAt30() {
        List<SaleItem> sales = new ArrayList<>();
        for (int i = 0; i < 40; i++) sales.add(sale("10", "1", Instant.ofEpochSecond(1_000_000 + i)));

        List<PublicKhataResponse.Entry> e = service.entries(sales, List.of());

        assertEquals(KhataLinkService.MAX_ENTRIES, e.size());
        assertEquals(Instant.ofEpochSecond(1_000_039), e.get(0).at());
    }

    @Test
    void newToken_IsUrlSafeAndUnique() {
        String a = KhataLinkService.newToken();
        assertTrue(a.matches("[A-Za-z0-9_-]{32}"));
        assertNotEquals(a, KhataLinkService.newToken());
    }
}
