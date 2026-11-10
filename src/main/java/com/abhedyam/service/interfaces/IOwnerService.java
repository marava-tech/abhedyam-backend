package com.abhedyam.service.interfaces;

import com.abhedyam.dto.OwnerResponse;
import com.abhedyam.dto.OwnerSummaryResponse;
import com.abhedyam.dto.OwnerUpdateRequest;

import java.util.UUID;

public interface IOwnerService {
    OwnerResponse getById(UUID id);
    OwnerResponse updateCurrentOwner(OwnerUpdateRequest request);
    OwnerResponse updateOwnerForOwner(UUID ownerId, OwnerUpdateRequest request);
    OwnerSummaryResponse getOwnerSummary(UUID ownerId);
}

