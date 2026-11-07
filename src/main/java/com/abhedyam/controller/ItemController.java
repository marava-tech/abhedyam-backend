package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.ItemListResponse;
import com.abhedyam.dto.ItemRequest;
import com.abhedyam.dto.ItemResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.StockChangeRequest;
import com.abhedyam.dto.StockMovementResponse;
import com.abhedyam.service.interfaces.IItemService;
import com.abhedyam.service.interfaces.IPurchaseService;
import com.abhedyam.dto.ToBuyLine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/items")
@RequiredArgsConstructor
@Tag(name = "Items and stock", description = "Items the shop sells, their stock and every stock change")
public class ItemController {

    private final IItemService service;
    private final IPurchaseService purchases;

    @GetMapping
    @Operation(summary = "Items with stock", description = "Out of stock first, then low, then by name. filter = ALL | LOW | OUT | NOT_SELLING (stock not sold in 60 days). summary counts every active item and the stock value (stock × buying price).")
    public ApiResponse<ItemListResponse> list(@PathVariable UUID ownerId,
                                              @RequestParam(defaultValue = "ALL") IItemService.Filter filter,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(service.list(ownerId, filter, search, page, size));
    }

    @GetMapping("/to-buy")
    @Operation(summary = "What to buy next", description = "Tracked items that are out, low, or will run out within 7 days at their 30-day sales rate. suggestedQuantity = 14 days of sales minus stock (at least the last purchase quantity for out/low items). Includes the last supplier and buying price.")
    public ApiResponse<java.util.List<ToBuyLine>> toBuy(@PathVariable UUID ownerId) {
        return ApiResponse.success(purchases.toBuy(ownerId));
    }

    @PostMapping
    @Operation(summary = "Add an item", description = "name and price required. openingStock turns stock tracking on.")
    public ApiResponse<ItemResponse> create(@PathVariable UUID ownerId, @RequestBody ItemRequest request) {
        return ApiResponse.success(service.create(ownerId, request));
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Edit an item", description = "Only the fields sent change. openingStock sets the counted stock.")
    public ApiResponse<ItemResponse> update(@PathVariable UUID ownerId, @PathVariable UUID itemId, @RequestBody ItemRequest request) {
        return ApiResponse.success(service.update(ownerId, itemId, request));
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Delete an item", description = "Hides it from the list; its history and past sales stay")
    public ApiResponse<Void> archive(@PathVariable UUID ownerId, @PathVariable UUID itemId) {
        service.archive(ownerId, itemId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{itemId}/stock")
    @Operation(summary = "Change stock", description = "RECEIVED adds, REMOVED subtracts (reason DAMAGED/USED/RETURNED/OTHER), COUNT sets what you counted. Turns tracking on.")
    public ApiResponse<ItemResponse> changeStock(@PathVariable UUID ownerId, @PathVariable UUID itemId, @RequestBody StockChangeRequest request) {
        return ApiResponse.success(service.changeStock(ownerId, itemId, request));
    }

    @GetMapping("/{itemId}/movements")
    @Operation(summary = "Stock history", description = "Every stock change for the item, newest first")
    public ApiResponse<PageResponse<StockMovementResponse>> movements(@PathVariable UUID ownerId, @PathVariable UUID itemId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(service.movements(ownerId, itemId, page, size));
    }
}
