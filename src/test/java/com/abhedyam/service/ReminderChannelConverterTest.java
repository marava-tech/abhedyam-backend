package com.abhedyam.service;

import com.abhedyam.model.ReminderChannelConverter;
import com.abhedyam.model.enums.ReminderChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReminderChannelConverterTest {
    private final ReminderChannelConverter c = new ReminderChannelConverter();

    @Test
    void oldRowsWithRemovedChannelsReadAsInApp() {
        for (String v : new String[] {"IN_APP", "SMS", "WHATSAPP", "EMAIL", null}) {
            assertEquals(ReminderChannel.IN_APP, c.convertToEntityAttribute(v));
        }
    }

    @Test
    void alwaysWritesInApp() {
        assertEquals("IN_APP", c.convertToDatabaseColumn(ReminderChannel.IN_APP));
    }
}
