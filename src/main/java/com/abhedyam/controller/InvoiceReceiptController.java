package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.InvoiceResponse;
import com.abhedyam.dto.ReceiptResponse;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.service.InvoiceReceiptService;
import com.abhedyam.service.interfaces.ISubscriptionService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Invoices & Receipts", description = "API for retrieving invoices and receipts")
public class InvoiceReceiptController {

    private final InvoiceReceiptService invoiceReceiptService;
    private final ISubscriptionService subscriptionService;
    private final OwnerRepository ownerRepository;

    @Value("${app.subscription.enforce:false}")
    private boolean enforcePro;

    private void maybeEnsurePro() {
        if (!enforcePro) {
            return;
        }
        UUID userId = SecurityUtil.getCurrentUserId();
        if (ownerRepository.existsById(userId)) {
            subscriptionService.ensureProSubscription(userId);
        }
    }

    @GetMapping("/invoices/customer/{customerId}")
    @Operation(
        summary = "Get invoice by customer ID",
        description = "Returns invoice generated at the start of sale."
    )
    public ApiResponse<InvoiceResponse> getInvoiceByCustomerId(
            @PathVariable UUID customerId,
            @RequestParam(required = false) UUID saleItemId) {
        maybeEnsurePro();
        return ApiResponse.success(invoiceReceiptService.getInvoiceByCustomerId(customerId, saleItemId));
    }

    @GetMapping("/receipts/customer/{customerId}")
    @Operation(
        summary = "Get receipt by customer ID",
        description = "Returns receipt for a sale item."
    )
    public ApiResponse<ReceiptResponse> getReceiptByCustomerId(
            @PathVariable UUID customerId,
            @RequestParam(required = false) UUID saleItemId) {
        maybeEnsurePro();
        return ApiResponse.success(invoiceReceiptService.getReceiptByCustomerId(customerId, saleItemId));
    }
}
