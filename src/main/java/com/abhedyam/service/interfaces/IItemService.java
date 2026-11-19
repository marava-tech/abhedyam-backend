package com.abhedyam.service.interfaces;

import com.abhedyam.dto.ItemListResponse;
import com.abhedyam.dto.ItemRequest;
import com.abhedyam.dto.ItemResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.StockChangeRequest;
import com.abhedyam.dto.StockMovementResponse;

import java.util.UUID;

public interface IItemService {
    enum Filter { ALL, LOW, OUT }

    ItemListResponse list(UUID ownerId, Filter filter, String search, int page, int size);

    /** Every active item of the owner with stock, unfiltered (for the morning digest). */
    java.util.List<ItemResponse> all(UUID ownerId);

    ItemResponse create(UUID ownerId, ItemRequest request);

    ItemResponse update(UUID ownerId, UUID itemId, ItemRequest request);

    void archive(UUID ownerId, UUID itemId);

    ItemResponse changeStock(UUID ownerId, UUID itemId, StockChangeRequest request);

    /** "Got more": adds stock to several items at once (one RECEIVED movement each), all or nothing. */
    java.util.List<ItemResponse> gotMore(UUID ownerId, com.abhedyam.dto.GotMoreRequest request);

    PageResponse<StockMovementResponse> movements(UUID ownerId, UUID itemId, int page, int size);
}
