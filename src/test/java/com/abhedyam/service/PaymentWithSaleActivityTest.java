package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.PaymentCreateRequest;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Payment;
import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.model.Product;
import com.abhedyam.model.SaleItem;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.security.UserPrincipal;
import com.abhedyam.service.interfaces.IAuditService;
import java.math.BigDecimal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Money taken with a sale writes no second "Payment from" activity row; a Payment in still does. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentWithSaleActivityTest {

    @Mock PaymentRepository paymentRepository;
    @Mock CustomerRepository customerRepository;
    @Mock ProductRepository productRepository;
    @Mock SaleItemRepository saleItemRepository;
    @Mock IAuditService auditService;
    @InjectMocks PaymentService paymentService;

    UUID owner = UUID.randomUUID();
    UUID saleItemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(owner, null), null, List.of()));
        SaleItem item = new SaleItem();
        item.setId(saleItemId);
        item.setOwnerId(owner);
        item.setCustomerId(UUID.randomUUID());
        item.setProductId(UUID.randomUUID());
        item.setPrice(new BigDecimal("1000"));
        item.setQuantity(BigDecimal.ONE);
        item.setRemainingAmount(new BigDecimal("1000"));
        when(saleItemRepository.findById(saleItemId)).thenReturn(Optional.of(item));
        when(customerRepository.findById(any())).thenReturn(Optional.of(new Customer()));
        when(productRepository.findById(any())).thenReturn(Optional.of(new Product()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    PaymentCreateRequest pay(String amount) {
        PaymentCreateRequest r = new PaymentCreateRequest();
        r.setSaleItemId(saleItemId);
        r.setAmount(new BigDecimal(amount));
        r.setMedium(PaymentMedium.CASH);
        r.setStatus(PaymentStatus.SUCCESS);
        return r;
    }

    @Test
    void paidWithSale_NoPaymentActivity() {
        assertNotNull(paymentService.createPaymentWithSale(pay("400")));
        verifyNoInteractions(auditService);
    }

    @Test
    void paymentIn_StillLogsActivity() {
        paymentService.createManualPayment(pay("400"));
        verify(auditService).logPaymentSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
