package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.ProductSearchRequest;
import com.abhedyam.dto.ProductWithStockResponse;
import com.abhedyam.service.interfaces.IProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/products")
@RequiredArgsConstructor
@Tag(name = "Owner Products", description = "Owner-scoped product APIs")
public class OwnerProductController {

    private final IProductService productService;

    @GetMapping
    @Operation(summary = "List products", description = "List products for an owner with search, pagination, and current stock")
    public ApiResponse<PageResponse<ProductWithStockResponse>> listProducts(
            @PathVariable UUID ownerId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setSearchTerm(q);
        request.setIsActive(isActive);
        request.setPage(page);
        request.setSize(size);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        return ApiResponse.success(productService.searchProductsByOwner(ownerId, request));
    }

}


