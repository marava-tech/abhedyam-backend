package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.CollectPaymentRequest;
import com.abhedyam.dto.CollectPaymentResponse;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Double tap / retry of Payment in with the same key records nothing the second time. */
@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> ops;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CustomerRepository customerRepository;
    @InjectMocks private PaymentService paymentService;

    @Test
    void repeatedKey_RecordsNothing() {
        UUID ownerId = UUID.randomUUID();
        when(paymentRepository.findByOwnerIdAndClientKey(ownerId, "k1")).thenReturn(java.util.List.of());
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("payment:idempotency:client:" + ownerId + ":k1"), eq("1"), any(Duration.class))).thenReturn(false);

        CollectPaymentRequest req = new CollectPaymentRequest();
        req.setCustomerId(UUID.randomUUID());
        req.setAmount(new BigDecimal("500"));
        req.setIdempotencyKey("k1");

        CollectPaymentResponse r = paymentService.collectFromCustomer(ownerId, req);

        assertTrue(r.getPayments().isEmpty());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(customerRepository);
    }

    /** Saved offline, sent again hours later (Redis forgot it): the earlier rows come back, nothing new is recorded. */
    @Test
    void keySeenBefore_ReturnsEarlierRows() {
        UUID ownerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        com.abhedyam.model.Payment p = new com.abhedyam.model.Payment();
        p.setId(UUID.randomUUID());
        p.setCustomerId(customerId);
        p.setAmount(new BigDecimal("300"));
        com.abhedyam.model.Payment q = new com.abhedyam.model.Payment();
        q.setId(UUID.randomUUID());
        q.setCustomerId(customerId);
        q.setAmount(new BigDecimal("200"));
        when(paymentRepository.findByOwnerIdAndClientKey(ownerId, "k1")).thenReturn(java.util.List.of(p, q));

        CollectPaymentRequest req = new CollectPaymentRequest();
        req.setCustomerId(customerId);
        req.setAmount(new BigDecimal("500"));
        req.setIdempotencyKey(" k1 ");

        CollectPaymentResponse r = paymentService.collectFromCustomer(ownerId, req);

        assertEquals(java.util.List.of(p.getId(), q.getId()), r.getPayments().stream().map(x -> x.getId()).toList());
        assertEquals(0, new BigDecimal("500").compareTo(r.getTotalCollected()));
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(customerRepository, redisTemplate);
    }
}
