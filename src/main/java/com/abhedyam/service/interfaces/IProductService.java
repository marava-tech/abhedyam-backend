package com.abhedyam.service.interfaces;

import com.abhedyam.dto.ProductWithStockResponse;

import java.util.List;
import java.util.UUID;

public interface IProductService {
    List<ProductWithStockResponse> getProductsWithStockByOwnerId(UUID ownerId);

    void evictOwnerCaches(UUID ownerId);
}
