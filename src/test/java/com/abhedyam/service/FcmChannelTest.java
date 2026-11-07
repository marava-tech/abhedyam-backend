package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FcmChannelTest {
    @Test
    void eachKindGoesToItsOwnChannel() {
        assertEquals("payments", FcmService.channelFor(Map.of("category", "PAYMENT_NEEDS_OK", "relatedEntityType", "PAYMENT")));
        assertEquals("summary", FcmService.channelFor(Map.of("category", "DUE_DIGEST", "relatedEntityType", "DIGEST")));
        assertEquals("dues", FcmService.channelFor(Map.of("category", "OWNER_REMINDER", "relatedEntityType", "DUE")));
        assertEquals("dues", FcmService.channelFor(Map.of("category", "OWNER_REMINDER", "relatedEntityType", "VISIT")));
        assertEquals("reminders", FcmService.channelFor(Map.of("category", "OWNER_REMINDER", "relatedEntityType", "REMINDER")));
        assertEquals("reminders", FcmService.channelFor(Map.of()));
    }
}
