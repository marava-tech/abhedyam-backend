package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.PaymentDueRequest;
import com.abhedyam.dto.PaymentDueResponse;
import com.abhedyam.dto.UpcomingDuesResponse;
import com.abhedyam.service.interfaces.IPaymentDueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}")
@RequiredArgsConstructor
@Tag(name = "Payment Dues", description = "Promised part-payments of a customer's balance, each with its own date")
public class PaymentDueController {

    private final IPaymentDueService service;

    @GetMapping("/dues")
    @Operation(summary = "Upcoming and missed dues", description = "All customers: missed dues first, then unpaid dues due within `days` (default 7, max 90). Paid dues are left out.")
    public ApiResponse<UpcomingDuesResponse> upcoming(@PathVariable UUID ownerId, @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(service.upcoming(ownerId, days));
    }

    @GetMapping("/customers/{customerId}/dues")
    @Operation(summary = "List dues", description = "Dues sorted by date with paid/remaining/status, plus balance, planned, unplanned, missed count and the next due. Payments fill the earliest due that existed when they were recorded.")
    public ApiResponse<CustomerDuesResponse> list(@PathVariable UUID ownerId, @PathVariable UUID customerId) {
        return ApiResponse.success(service.list(ownerId, customerId));
    }

    @PostMapping("/customers/{customerId}/dues")
    @Operation(summary = "Add a due", description = "amount and dueDate required; date not in the past; all unpaid dues together can't exceed the balance")
    public ApiResponse<PaymentDueResponse> create(@PathVariable UUID ownerId, @PathVariable UUID customerId,
                                                  @RequestBody PaymentDueRequest request) {
        return ApiResponse.success(service.create(ownerId, customerId, request));
    }

    @PutMapping("/customers/{customerId}/dues/{dueId}")
    @Operation(summary = "Edit a due", description = "Only the fields sent change; the amount can't go below what is already paid against it")
    public ApiResponse<PaymentDueResponse> update(@PathVariable UUID ownerId, @PathVariable UUID customerId,
                                                  @PathVariable UUID dueId, @RequestBody PaymentDueRequest request) {
        return ApiResponse.success(service.update(ownerId, customerId, dueId, request));
    }

    @DeleteMapping("/customers/{customerId}/dues/{dueId}")
    @Operation(summary = "Delete a due")
    public ApiResponse<Void> delete(@PathVariable UUID ownerId, @PathVariable UUID customerId, @PathVariable UUID dueId) {
        service.delete(ownerId, customerId, dueId);
        return ApiResponse.success(null);
    }
}
