package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.CollectionVisitRequest;
import com.abhedyam.dto.CollectionVisitResponse;
import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.PaymentDueRequest;
import com.abhedyam.dto.PaymentDueResponse;
import com.abhedyam.dto.UpcomingDuesResponse;
import com.abhedyam.dto.FollowUpRequest;
import com.abhedyam.dto.FollowUpResponse;
import com.abhedyam.service.interfaces.IPaymentDueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}")
@RequiredArgsConstructor
@Tag(name = "Upcoming collections", description = "Customer dues (amount + date) and village visits, in date order")
public class PaymentDueController {

    private final IPaymentDueService service;

    @GetMapping("/dues")
    @Operation(summary = "Upcoming and overdue dues", description = "Upcoming collections: overdue dues first, then unpaid dues within `days` (default 7, max 365), plus open village visits in the same window. Paid dues and done visits are left out.")
    public ApiResponse<UpcomingDuesResponse> upcoming(@PathVariable UUID ownerId, @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(service.upcoming(ownerId, days));
    }

    @PostMapping("/collection-visits")
    @Operation(summary = "Plan a village visit", description = "village + visitDate (today or later), optional note")
    public ApiResponse<CollectionVisitResponse> createVisit(@PathVariable UUID ownerId, @RequestBody CollectionVisitRequest request) {
        return ApiResponse.success(service.createVisit(ownerId, request));
    }

    @PutMapping("/collection-visits/{visitId}")
    @Operation(summary = "Move, edit or mark a village visit done", description = "Only the fields sent change; done=true removes it from the list")
    public ApiResponse<CollectionVisitResponse> updateVisit(@PathVariable UUID ownerId, @PathVariable UUID visitId,
                                                            @RequestBody CollectionVisitRequest request) {
        return ApiResponse.success(service.updateVisit(ownerId, visitId, request));
    }

    @DeleteMapping("/collection-visits/{visitId}")
    @Operation(summary = "Delete a village visit")
    public ApiResponse<Void> deleteVisit(@PathVariable UUID ownerId, @PathVariable UUID visitId) {
        service.deleteVisit(ownerId, visitId);
        return ApiResponse.success(null);
    }

    @PostMapping("/customers/{customerId}/follow-ups")
    @Operation(summary = "Call a customer again on a date", description = "date (today or later), optional note. Pushes to the owner at 10 am that day; shows in Upcoming collections until done")
    public ApiResponse<FollowUpResponse> createFollowUp(@PathVariable UUID ownerId, @PathVariable UUID customerId,
                                                        @RequestBody FollowUpRequest request) {
        return ApiResponse.success(service.createFollowUp(ownerId, customerId, request));
    }

    @PutMapping("/follow-ups/{followUpId}")
    @Operation(summary = "Move, edit or mark a follow-up done", description = "Only the fields sent change; done=true removes it from the list (data is null then)")
    public ApiResponse<FollowUpResponse> updateFollowUp(@PathVariable UUID ownerId, @PathVariable UUID followUpId,
                                                        @RequestBody FollowUpRequest request) {
        return ApiResponse.success(service.updateFollowUp(ownerId, followUpId, request));
    }

    @DeleteMapping("/follow-ups/{followUpId}")
    @Operation(summary = "Delete a follow-up")
    public ApiResponse<Void> deleteFollowUp(@PathVariable UUID ownerId, @PathVariable UUID followUpId) {
        service.deleteFollowUp(ownerId, followUpId);
        return ApiResponse.success(null);
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
