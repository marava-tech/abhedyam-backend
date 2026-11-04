package com.abhedyam.model.enums;

/**
 * How a reminder is delivered. Only IN_APP (in-app row + push) is supported.
 * EMAIL / SMS / WHATSAPP were accepted by the API but never delivered, so they were removed.
 */
public enum ReminderChannel {
    IN_APP
}
