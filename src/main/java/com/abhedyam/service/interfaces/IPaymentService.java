package com.abhedyam.service.interfaces;

import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.PaymentCreateRequest;
import com.abhedyam.dto.PaymentResponse;
import com.abhedyam.dto.PaymentStatusUpdateRequest;
import com.abhedyam.model.Payment;

import java.util.List;
import java.util.UUID;

public interface IPaymentService {
    Payment create(Payment payment);
    PaymentResponse createManualPayment(PaymentCreateRequest request);

    com.abhedyam.dto.CollectPaymentResponse collectFromCustomer(java.util.UUID ownerId, com.abhedyam.dto.CollectPaymentRequest request);

    com.abhedyam.dto.CollectPaymentResponse writeOffCustomer(java.util.UUID ownerId, java.util.UUID customerId);

    int undoWriteOff(java.util.UUID ownerId, java.util.UUID customerId);

    /** Owner took a payment by mistake: puts each amount back on its sale and marks the rows failed (kept, not deleted). Returns how many changed. */
    int cancelPayments(java.util.UUID ownerId, java.util.List<java.util.UUID> paymentIds);
    Payment getById(UUID id);
    List<PaymentResponse> filterPayments(String searchText);
    PageResponse<PaymentResponse> getOwnerPayments(UUID ownerId, String searchText, Integer page, Integer size, String sortBy, String sortDirection);
    List<Payment> getByCustomerId(UUID customerId);
    Payment updateStatus(UUID id, PaymentStatusUpdateRequest request);
}

