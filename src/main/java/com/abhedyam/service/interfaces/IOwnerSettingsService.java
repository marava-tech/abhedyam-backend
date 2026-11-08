package com.abhedyam.service.interfaces;

import com.abhedyam.dto.OwnerSettingsResponse;
import com.abhedyam.dto.OwnerSettingsUpdateRequest;

import java.util.UUID;

public interface IOwnerSettingsService {
    OwnerSettingsResponse getOwnerSettings(UUID ownerId);
    OwnerSettingsResponse updateOwnerSettings(UUID ownerId, OwnerSettingsUpdateRequest request);

    /** Who to chase "called today": adds [customerId] to otherFlags.chaseContacted for today (IST), under a row lock. */
    OwnerSettingsResponse addChaseContacted(UUID ownerId, String customerId);
    /** Villages view on for this owner (no caller check; for jobs and other services). */
    boolean villageMode(UUID ownerId);
}

