package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Payment;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.util.SecurityUtil;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

/** Undo of a Payment in: amounts go back on the sales, rows stay as failed, retries and strangers are safe. */
@ExtendWith(MockitoExtension.class)
class CancelPaymentsTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SaleItemRepository saleItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @InjectMocks private PaymentService paymentService;

    private final UUID owner = UUID.randomUUID();

    private Payment row(UUID owner, PaymentStatus status, UUID saleItemId, String amount) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOwnerId(owner);
        p.setCustomerId(UUID.randomUUID());
        p.setSaleItemId(saleItemId);
        p.setAmount(new BigDecimal(amount));
        p.setStatus(status);
        return p;
    }

    @Test
    void cancels_success_rows_skips_already_cancelled_and_puts_money_back() {
        UUID saleId = UUID.randomUUID();
        SaleItem sale = new SaleItem();
        sale.setPrice(new BigDecimal("1000"));
        sale.setQuantity(BigDecimal.ONE);
        sale.setRemainingAmount(new BigDecimal("400")); // 600 paid so far
        Payment live = row(owner, PaymentStatus.SUCCESS, saleId, "100");
        Payment done = row(owner, PaymentStatus.FAILED, saleId, "50");
        when(paymentRepository.findAllById(any())).thenReturn(List.of(live, done));
        when(saleItemRepository.findById(saleId)).thenReturn(Optional.of(sale));

        try (MockedStatic<SecurityUtil> sec = mockStatic(SecurityUtil.class)) {
            sec.when(SecurityUtil::getCurrentUserId).thenReturn(owner);
            int n = paymentService.cancelPayments(owner, List.of(live.getId(), done.getId()));
            assertEquals(1, n);
        }
        assertEquals(PaymentStatus.FAILED, live.getStatus());
        assertEquals("Cancelled by owner", live.getReference());
        assertEquals(0, new BigDecimal("500").compareTo(sale.getRemainingAmount()));
    }

    @Test
    void another_owners_row_rejects_everything() {
        Payment mine = row(owner, PaymentStatus.SUCCESS, null, "10");
        Payment theirs = row(UUID.randomUUID(), PaymentStatus.SUCCESS, null, "10");
        when(paymentRepository.findAllById(any())).thenReturn(List.of(mine, theirs));
        try (MockedStatic<SecurityUtil> sec = mockStatic(SecurityUtil.class)) {
            sec.when(SecurityUtil::getCurrentUserId).thenReturn(owner);
            assertThrows(BusinessException.class, () -> paymentService.cancelPayments(owner, List.of(mine.getId(), theirs.getId())));
        }
        verify(paymentRepository, never()).saveAll(any());
    }
}
