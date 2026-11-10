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

    @PostMapping("/collect")
    @Operation(summary = "Collect a payment from a customer",
            description = "Applies the amount to the customer's oldest unpaid sale items first. Rejected if it exceeds the total due.")
    public ApiResponse<com.abhedyam.dto.CollectPaymentResponse> collect(
            @PathVariable UUID ownerId,
            @jakarta.validation.Valid @RequestBody com.abhedyam.dto.CollectPaymentRequest request) {
        return ApiResponse.success(paymentService.collectFromCustomer(ownerId, request));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel payments recorded by mistake",
            description = "Takes the ids from the collect response. Each amount goes back on its sale; the rows stay in history as failed. Safe to repeat.")
    public ApiResponse<java.util.Map<String, Integer>> cancel(
            @PathVariable UUID ownerId,
            @jakarta.validation.Valid @RequestBody com.abhedyam.dto.CancelPaymentsRequest request) {
        return ApiResponse.success(java.util.Map.of("cancelled", paymentService.cancelPayments(ownerId, request.getPaymentIds())));
    }

    @PostMapping("/write-off")
    @Operation(summary = "Write off a customer's whole balance",
            description = "For dues that will not come back. Closes the open balance oldest sale first; kept in the ledger as 'Written off', never counted as collected. Undo by deleting those payments.")
    public ApiResponse<com.abhedyam.dto.CollectPaymentResponse> writeOff(
            @PathVariable UUID ownerId,
            @RequestBody java.util.Map<String, UUID> body) {
        UUID customerId = body.get("customerId");
        if (customerId == null) {
            throw new com.abhedyam.exception.BusinessException(com.abhedyam.constants.ErrorCodes.VALIDATION_ERROR, "customerId is required");
        }
        return ApiResponse.success(paymentService.writeOffCustomer(ownerId, customerId));
    }

    @DeleteMapping("/write-off/{customerId}")
    @Operation(summary = "Undo a write-off", description = "Puts the written-off amounts back on the customer's sales.")
    public ApiResponse<java.util.Map<String, Integer>> undoWriteOff(@PathVariable UUID ownerId, @PathVariable UUID customerId) {
        return ApiResponse.success(java.util.Map.of("removed", paymentService.undoWriteOff(ownerId, customerId)));
    }

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


