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
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("payment:idempotency:client:" + ownerId + ":k1"), eq("1"), any(Duration.class))).thenReturn(false);

        CollectPaymentRequest req = new CollectPaymentRequest();
        req.setCustomerId(UUID.randomUUID());
        req.setAmount(new BigDecimal("500"));
        req.setIdempotencyKey("k1");

        CollectPaymentResponse r = paymentService.collectFromCustomer(ownerId, req);

        assertTrue(r.getPayments().isEmpty());
        verifyNoInteractions(paymentRepository, customerRepository);
    }
}
