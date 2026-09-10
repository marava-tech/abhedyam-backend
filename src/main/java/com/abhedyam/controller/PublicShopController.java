package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.OwnerPublicResponse;
import com.abhedyam.dto.ProductWithStockResponse;
import com.abhedyam.dto.PublicPaymentLinkResponse;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.service.PaymentLinkService;
import com.abhedyam.service.interfaces.IProductService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicShopController {

    private final OwnerRepository ownerRepository;
    private final IProductService productService;
    private final PaymentLinkService paymentLinkService;

    @GetMapping("/payment-links/{token}")
    public ApiResponse<PublicPaymentLinkResponse> paymentLink(@PathVariable String token) {
        return ApiResponse.success(paymentLinkService.getPublic(token));
    }

    @GetMapping("/shops")
    public ApiResponse<List<OwnerPublicResponse>> listShops() {
        List<OwnerPublicResponse> shops = ownerRepository.findByPublicListingEnabledTrueAndIsActiveTrue()
                .stream()
                .filter(o -> o.getPublicSlug() != null && !o.getPublicSlug().isBlank())
                .map(this::toPublic)
                .toList();
        return ApiResponse.success(shops);
    }

    @GetMapping("/shops/{slug}")
    public ApiResponse<PublicShopDetail> shop(@PathVariable String slug) {
        Owner owner = ownerRepository.findByPublicSlug(slug)
                .filter(o -> Boolean.TRUE.equals(o.getPublicListingEnabled()))
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        PublicShopDetail detail = new PublicShopDetail();
        detail.setShop(toPublic(owner));
        detail.setCatalog(productService.getProductsWithStockByOwnerId(owner.getId()));
        return ApiResponse.success(detail);
    }

    private OwnerPublicResponse toPublic(Owner owner) {
        OwnerPublicResponse response = new OwnerPublicResponse();
        response.setId(owner.getId());
        response.setName(owner.getName());
        response.setBusinessName(owner.getBusinessName());
        response.setPhone(owner.getPhone());
        response.setImageUrl(owner.getImageUrl());
        response.setIsVerified(owner.getIsVerified());
        response.setPublicSlug(owner.getPublicSlug());
        return response;
    }

    @Data
    public static class PublicShopDetail {
        private OwnerPublicResponse shop;
        private List<ProductWithStockResponse> catalog;
    }
}
