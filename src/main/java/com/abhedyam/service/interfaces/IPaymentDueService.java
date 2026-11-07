package com.abhedyam.service.interfaces;

import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.PaymentDueRequest;
import com.abhedyam.dto.PaymentDueResponse;

import java.util.UUID;

public interface IPaymentDueService {
    CustomerDuesResponse list(UUID ownerId, UUID customerId);

    PaymentDueResponse create(UUID ownerId, UUID customerId, PaymentDueRequest request);

    PaymentDueResponse update(UUID ownerId, UUID customerId, UUID dueId, PaymentDueRequest request);

    void delete(UUID ownerId, UUID customerId, UUID dueId);
}
