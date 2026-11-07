package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/** A due with the customer it belongs to, for owner-wide lists (Home "Due this week", digest, reminders). */
@Data
@AllArgsConstructor
public class OwnerDueResponse {
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private String village;
    private PaymentDueResponse due;
}
