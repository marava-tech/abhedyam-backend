package com.abhedyam.model;

import com.abhedyam.model.enums.ReminderChannel;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Rows saved before EMAIL/SMS/WHATSAPP were removed still hold those strings; read them as IN_APP. */
@Converter
public class ReminderChannelConverter implements AttributeConverter<ReminderChannel, String> {
    @Override
    public String convertToDatabaseColumn(ReminderChannel channel) {
        return ReminderChannel.IN_APP.name();
    }

    @Override
    public ReminderChannel convertToEntityAttribute(String dbValue) {
        return ReminderChannel.IN_APP;
    }
}
