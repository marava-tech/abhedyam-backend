package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;

import com.abhedyam.exception.BusinessException;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class NotificationSettingsValidationTest {
    @Test
    void parsesGoodTimeAndZone() {
        assertEquals(LocalTime.of(21, 30), NotificationSettingsService.time("21:30"));
        assertEquals("Asia/Kolkata", NotificationSettingsService.zone("Asia/Kolkata"));
    }

    @Test
    void rejectsBadTimeAndZone() {
        assertThrows(BusinessException.class, () -> NotificationSettingsService.time("9pm"));
        assertThrows(BusinessException.class, () -> NotificationSettingsService.time("25:00"));
        assertThrows(BusinessException.class, () -> NotificationSettingsService.zone("Mars/Base"));
    }
}
