package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.CustomerCreateRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.SaleItem;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.security.UserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Spec hide-remove-customer.md: hide only at ₹0, remove only without records, adding a hidden customer again brings them back. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerHideTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private SaleItemRepository saleItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private LocationDetailsRepository locationDetailsRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @InjectMocks private CustomerService customerService;

    private final UUID ownerId = UUID.randomUUID();
    private Customer c;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(ownerId, null), null, List.of()));
        c = new Customer();
        c.setId(UUID.randomUUID());
        c.setOwnerId(ownerId);
        c.setName("Ramu");
        c.setIsActive(true);
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(locationDetailsRepository.findByUserId(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void owing_CannotBeHidden() {
        when(saleItemRepository.sumTotalsByCustomer(ownerId)).thenReturn(List.<Object[]>of(new Object[]{c.getId().toString(), "500.00"}));
        when(paymentRepository.sumSuccessfulByCustomer(ownerId)).thenReturn(List.<Object[]>of(new Object[]{c.getId().toString(), "250.00"}));

        BusinessException e = assertThrows(BusinessException.class, () -> customerService.setHidden(ownerId, c.getId(), true));
        assertEquals("CUSTOMER_OWES", e.getErrorCode());
        assertTrue(e.getMessage().contains("250"));
        assertTrue(c.getIsActive());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void paidUp_IsHidden_AndShownAgain() {
        when(saleItemRepository.sumTotalsByCustomer(ownerId)).thenReturn(List.<Object[]>of(new Object[]{c.getId().toString(), "500.00"}));
        when(paymentRepository.sumSuccessfulByCustomer(ownerId)).thenReturn(List.<Object[]>of(new Object[]{c.getId().toString(), "500.00"}));

        assertTrue(customerService.setHidden(ownerId, c.getId(), true).isHidden());
        assertFalse(c.getIsActive());
        assertFalse(customerService.setHidden(ownerId, c.getId(), false).isHidden());
        assertTrue(c.getIsActive());
    }

    @Test
    void withSales_CannotBeRemoved() {
        when(saleItemRepository.findByCustomerIdAndOwnerId(c.getId(), ownerId)).thenReturn(List.of(new SaleItem()));

        BusinessException e = assertThrows(BusinessException.class, () -> customerService.deleteCustomer(ownerId, c.getId()));
        assertEquals("CUSTOMER_HAS_RECORDS", e.getErrorCode());
        verify(customerRepository, never()).delete(any());
    }

    @Test
    void addedAgainByPhone_ComesBack() {
        c.setIsActive(false); // hidden
        when(customerRepository.findByPhoneNormalized(any())).thenReturn(Optional.of(c));
        CustomerCreateRequest req = new CustomerCreateRequest();
        req.setName("Ramu");
        req.setPhone("9876543210");

        assertSame(c, customerService.create(req));
        assertTrue(c.getIsActive());
    }
}
