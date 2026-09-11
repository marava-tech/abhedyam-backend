package com.abhedyam.service.interfaces;

import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.ProductCreateRequest;
import com.abhedyam.dto.ProductSearchRequest;
import com.abhedyam.dto.ProductSearchResult;
import com.abhedyam.dto.ProductUpdateRequest;
import com.abhedyam.dto.ProductWithStockResponse;
import com.abhedyam.model.Product;

import java.util.List;
import java.util.UUID;

public interface IProductService {
    Product create(ProductCreateRequest request);
    Product getById(UUID id);
    List<Product> getByOwnerId(UUID ownerId);
    List<ProductWithStockResponse> getProductsWithStockByOwnerId(UUID ownerId);
    PageResponse<ProductWithStockResponse> searchProducts(ProductSearchRequest request);
    PageResponse<ProductWithStockResponse> searchProductsByOwner(UUID ownerId, ProductSearchRequest request);
    List<ProductSearchResult> searchByName(String name);
    Product updateProduct(ProductUpdateRequest request);
    Product toggleActive(UUID id);
}

