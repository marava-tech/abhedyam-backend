package com.abhedyam.service.interfaces;

import com.abhedyam.dto.BillingStatusResponse;

import java.util.Map;
import java.util.UUID;

public interface IBillingService {
    BillingStatusResponse status(UUID ownerId);

    /** Applies one RevenueCat webhook event. Returns false when it was ignored (unknown owner, no expiry). */
    boolean applyRevenueCatEvent(Map<String, Object> event);

    void grantDays(UUID ownerId, int days);

    void endAccess(UUID ownerId);
}
