package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.PaymentLinkCreateRequest;
import com.abhedyam.dto.PaymentLinkResponse;
import com.abhedyam.service.PaymentLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-links")
@RequiredArgsConstructor
public class PaymentLinkController {

    private final PaymentLinkService paymentLinkService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentLinkResponse> create(@Valid @RequestBody PaymentLinkCreateRequest request) {
        return ApiResponse.success(paymentLinkService.create(request));
    }
}
