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
    Payment getById(UUID id);
    List<Payment> getByOwnerId(UUID ownerId);
    PageResponse<PaymentResponse> getMyPayments(Integer page, Integer size);
    List<PaymentResponse> filterPayments(String searchText);
    PageResponse<PaymentResponse> getOwnerPayments(UUID ownerId, String searchText, Integer page, Integer size, String sortBy, String sortDirection);
    List<Payment> getByCustomerId(UUID customerId);
    Payment updateStatus(UUID id, PaymentStatusUpdateRequest request);
}

