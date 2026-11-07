package com.abhedyam.service.interfaces;

import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.PurchaseRequest;
import com.abhedyam.dto.PurchaseResponse;

import java.util.UUID;

public interface IPurchaseService {
    enum Status { OPEN, ALL }

    PageResponse<PurchaseResponse> list(UUID ownerId, Status status, UUID itemId, int page, int size);

    PurchaseResponse get(UUID ownerId, UUID purchaseId);

    PurchaseResponse create(UUID ownerId, PurchaseRequest request);

    void delete(UUID ownerId, UUID purchaseId);
}
