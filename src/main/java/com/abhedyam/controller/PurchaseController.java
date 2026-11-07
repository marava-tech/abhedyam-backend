package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.PurchaseRequest;
import com.abhedyam.dto.PurchaseResponse;
import com.abhedyam.service.interfaces.IPurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/purchases")
@RequiredArgsConstructor
@Tag(name = "Purchases", description = "Stock bought in one go: what was paid, what came, what is left")
public class PurchaseController {

    private final IPurchaseService service;

    @GetMapping
    @Operation(summary = "Purchases, newest first", description = "status OPEN = something still left. itemId = only purchases with that item. left is worked out oldest stock first.")
    public ApiResponse<PageResponse<PurchaseResponse>> list(@PathVariable UUID ownerId,
                                                            @RequestParam(defaultValue = "ALL") IPurchaseService.Status status,
                                                            @RequestParam(required = false) UUID itemId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(ownerId, status, itemId, page, size));
    }

    @GetMapping("/{purchaseId}")
    @Operation(summary = "One purchase with what is left of each item")
    public ApiResponse<PurchaseResponse> get(@PathVariable UUID ownerId, @PathVariable UUID purchaseId) {
        return ApiResponse.success(service.get(ownerId, purchaseId));
    }

    @PostMapping
    @Operation(summary = "Add a purchase", description = "totalPaid is split over items without a unitCost by quantity × selling price. Adds the stock and sets each item's buying price.")
    public ApiResponse<PurchaseResponse> create(@PathVariable UUID ownerId, @RequestBody PurchaseRequest request) {
        return ApiResponse.success(service.create(ownerId, request));
    }

    @DeleteMapping("/{purchaseId}")
    @Operation(summary = "Delete a purchase", description = "Only while none of it is sold or removed; takes its stock back off")
    public ApiResponse<Void> delete(@PathVariable UUID ownerId, @PathVariable UUID purchaseId) {
        service.delete(ownerId, purchaseId);
        return ApiResponse.success(null);
    }
}
