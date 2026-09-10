package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.CustomerBasicSummaryResponse;
import com.abhedyam.dto.CustomerNotesSummaryResponse;
import com.abhedyam.dto.CustomerPaymentsSummaryResponse;
import com.abhedyam.dto.CustomerRemindersSummaryResponse;
import com.abhedyam.dto.CustomerResponse;
import com.abhedyam.dto.CustomerSalesSummaryResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.SaleItemResponse;
import com.abhedyam.service.interfaces.ICustomerService;
import com.abhedyam.service.interfaces.ISaleItemService;
import com.abhedyam.constants.QueryParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/customers")
@RequiredArgsConstructor
@Tag(name = "Owner Customers", description = "Owner-scoped customer APIs")
public class OwnerCustomerController {

    private final ICustomerService customerService;
    private final ISaleItemService saleItemService;

    @GetMapping
    @Operation(summary = "List customers", description = "List customers for an owner with search and pagination. Default order is by village (customers grouped by village then by name, those with no village last). Pass sortBy=createdAt (or another Customer field) with sortDirection to override. When includePendingAmountDetails is true, each customer includes pending amount (sale total minus paid). Use village param to filter by exact village name.")
    public ApiResponse<PageResponse<CustomerResponse>> listCustomers(
            @PathVariable UUID ownerId,
            @RequestParam(value = QueryParams.Q, required = false) String q,
            @RequestParam(value = "village", required = false) String village,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "village") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(value = QueryParams.INCLUDE_PENDING_AMOUNT_DETAILS, defaultValue = "false") Boolean includePendingAmountDetails) {
        return ApiResponse.success(customerService.getOwnerCustomers(ownerId, q, village, page, size, sortBy, sortDirection, Boolean.TRUE.equals(includePendingAmountDetails)));
    }

    @GetMapping("/{customerId}/summary")
    @Operation(summary = "Customer summary", description = "Get basic customer summary for an owner")
    public ApiResponse<CustomerBasicSummaryResponse> getCustomerSummary(
            @PathVariable UUID ownerId,
            @PathVariable UUID customerId) {
        return ApiResponse.success(customerService.getCustomerBasicSummary(ownerId, customerId));
    }

    @GetMapping("/{customerId}/sales-summary")
    @Operation(summary = "Customer sales summary", description = "Get sales summary for a customer")
    public ApiResponse<CustomerSalesSummaryResponse> getCustomerSalesSummary(
            @PathVariable UUID ownerId,
            @PathVariable UUID customerId) {
        return ApiResponse.success(customerService.getCustomerSalesSummary(ownerId, customerId));
    }

    @GetMapping("/{customerId}/payments-summary")
    @Operation(summary = "Customer payments summary", description = "Get payments summary for a customer")
    public ApiResponse<CustomerPaymentsSummaryResponse> getCustomerPaymentsSummary(
            @PathVariable UUID ownerId,
            @PathVariable UUID customerId) {
        return ApiResponse.success(customerService.getCustomerPaymentsSummary(ownerId, customerId));
    }

    @GetMapping("/{customerId}/notes-summary")
    @Operation(summary = "Customer notes summary", description = "Get notes summary for a customer")
    public ApiResponse<CustomerNotesSummaryResponse> getCustomerNotesSummary(
            @PathVariable UUID ownerId,
            @PathVariable UUID customerId) {
        return ApiResponse.success(customerService.getCustomerNotesSummary(ownerId, customerId));
    }

    @GetMapping("/{customerId}/reminders-summary")
    @Operation(summary = "Customer reminders summary", description = "Get reminders summary for a customer")
    public ApiResponse<CustomerRemindersSummaryResponse> getCustomerRemindersSummary(
            @PathVariable UUID ownerId,
            @PathVariable UUID customerId) {
        return ApiResponse.success(customerService.getCustomerRemindersSummary(ownerId, customerId));
    }

    @GetMapping("/{customerId}/sale-items")
    @Operation(summary = "Customer sale items", description = "Get sale items for a customer with product names")
    public ApiResponse<List<SaleItemResponse>> getCustomerSaleItems(
            @PathVariable UUID ownerId,
            @PathVariable UUID customerId) {
        return ApiResponse.success(saleItemService.getByCustomerIdForOwner(ownerId, customerId, true));
    }
}


