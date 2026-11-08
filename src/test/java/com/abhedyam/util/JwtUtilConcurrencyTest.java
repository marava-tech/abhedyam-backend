package com.abhedyam.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Home fires ~10 calls at once right after the app opens; the first ones after a restart must not 401. */
class JwtUtilConcurrencyTest {

    @Test
    void manyThreadsValidatingAtOnceOnFirstUseAllSucceed() throws Exception {
        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "a-test-secret-that-is-long-enough-for-hs256-0123456789");
        ReflectionTestUtils.setField(jwt, "expiration", 3_600_000L);
        String token = jwt.generateToken(UUID.randomUUID(), "9000000000");

        int n = 32;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) results.add(pool.submit(() -> { go.await(); return jwt.validateToken(token); }));
        go.countDown();
        int failed = 0;
        for (Future<Boolean> f : results) if (!f.get()) failed++;
        pool.shutdown();
        assertTrue(failed == 0, failed + " of " + n + " concurrent validations failed");
    }
}
