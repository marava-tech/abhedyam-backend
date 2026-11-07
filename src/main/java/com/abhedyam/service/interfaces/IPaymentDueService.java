package com.abhedyam.service.interfaces;

import com.abhedyam.dto.CollectionVisitRequest;
import com.abhedyam.dto.CollectionVisitResponse;
import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.OwnerDueResponse;
import com.abhedyam.dto.UpcomingDuesResponse;
import com.abhedyam.dto.PaymentDueRequest;
import com.abhedyam.dto.PaymentDueResponse;

import java.util.List;
import java.util.UUID;

public interface IPaymentDueService {
    CustomerDuesResponse list(UUID ownerId, UUID customerId);

    /** Every active due of the owner with its paid part, sorted by date. No access check: server-side callers only. */
    List<OwnerDueResponse> ownerDues(UUID ownerId);

    /** Overdue dues, then unpaid dues up to [days] from today. Caller must be the owner. */
    UpcomingDuesResponse upcoming(UUID ownerId, int days);

    PaymentDueResponse create(UUID ownerId, UUID customerId, PaymentDueRequest request);

    PaymentDueResponse update(UUID ownerId, UUID customerId, UUID dueId, PaymentDueRequest request);

    void delete(UUID ownerId, UUID customerId, UUID dueId);

    /** Open village visits with who owes there. No access check: server-side callers only. */
    List<CollectionVisitResponse> ownerVisits(UUID ownerId);

    CollectionVisitResponse createVisit(UUID ownerId, CollectionVisitRequest request);

    CollectionVisitResponse updateVisit(UUID ownerId, UUID visitId, CollectionVisitRequest request);

    void deleteVisit(UUID ownerId, UUID visitId);
}
