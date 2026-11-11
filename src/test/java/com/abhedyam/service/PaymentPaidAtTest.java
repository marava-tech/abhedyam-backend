package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import com.abhedyam.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Payment in for an earlier day: past dates up to a year are kept, future and very old dates are refused. */
class PaymentPaidAtTest {
    private final Instant now = Instant.parse("2026-11-11T07:00:00Z");

    @Test
    void nullMeansNow() {
        assertNull(PaymentService.checkPaidAt(null, now));
    }

    @Test
    void earlierDayKept() {
        Instant twoDaysAgo = now.minus(Duration.ofDays(2));
        assertEquals(twoDaysAgo, PaymentService.checkPaidAt(twoDaysAgo, now));
    }

    @Test
    void smallClockSlackAllowed() {
        assertNotNull(PaymentService.checkPaidAt(now.plus(Duration.ofMinutes(3)), now));
    }

    @Test
    void futureRefused() {
        assertThrows(BusinessException.class, () -> PaymentService.checkPaidAt(now.plus(Duration.ofDays(1)), now));
    }

    @Test
    void olderThanAYearRefused() {
        assertThrows(BusinessException.class, () -> PaymentService.checkPaidAt(now.minus(Duration.ofDays(400)), now));
    }
}
