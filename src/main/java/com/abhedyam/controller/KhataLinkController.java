package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.KhataLinkResponse;
import com.abhedyam.dto.PublicKhataResponse;
import com.abhedyam.service.interfaces.IKhataLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Khata link", description = "Private no-login link a customer opens from the shop's WhatsApp message")
public class KhataLinkController {

    private final IKhataLinkService service;

    @GetMapping("/api/v1/owners/{ownerId}/customers/{customerId}/link")
    @Operation(summary = "Customer's khata link", description = "Made on first call. Put it in reminders, receipts and statements.")
    public ApiResponse<KhataLinkResponse> get(@PathVariable UUID ownerId, @PathVariable UUID customerId) {
        return ApiResponse.success(service.get(ownerId, customerId));
    }

    @PostMapping("/api/v1/owners/{ownerId}/customers/{customerId}/link/reset")
    @Operation(summary = "Reset the khata link", description = "New link; the old one stops working at once.")
    public ApiResponse<KhataLinkResponse> reset(@PathVariable UUID ownerId, @PathVariable UUID customerId) {
        return ApiResponse.success(service.reset(ownerId, customerId));
    }

    @GetMapping("/api/v1/public/k/{token}")
    @Operation(summary = "Khata page (no login)", description = "Shop, balance, next due, UPI ID and recent entries for the customer behind the token. No customer phone.")
    public ApiResponse<PublicKhataResponse> view(@PathVariable String token) {
        return ApiResponse.success(service.view(token));
    }
}
