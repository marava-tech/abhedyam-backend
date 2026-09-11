package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.PaymentResponse;
import com.abhedyam.service.interfaces.IPaymentService;
import com.abhedyam.constants.QueryParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/payments")
@RequiredArgsConstructor
@Tag(name = "Owner Payments", description = "Owner-scoped payment APIs")
public class OwnerPaymentController {

    private final IPaymentService paymentService;

    @GetMapping
    @Operation(summary = "List payments", description = "List payments for an owner with search and pagination")
    public ApiResponse<PageResponse<PaymentResponse>> listPayments(
            @PathVariable UUID ownerId,
            @RequestParam(value = QueryParams.Q, required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ApiResponse.success(paymentService.getOwnerPayments(ownerId, q, page, size, sortBy, sortDirection));
    }
}


