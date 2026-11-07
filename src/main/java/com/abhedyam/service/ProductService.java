package com.abhedyam.service;

import com.abhedyam.dto.ProductWithStockResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Inventory;
import com.abhedyam.model.Product;
import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.service.interfaces.IProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements IProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    private static final String PRODUCTS_CACHE_PREFIX = "products:owner:";
    private static final String PRODUCTS_WITH_STOCK_CACHE_PREFIX = "products:with-stock:";
    private static final int CACHE_TTL_MINUTES = 5;




    @Override
    @Transactional(readOnly = true)
    public List<ProductWithStockResponse> getProductsWithStockByOwnerId(UUID ownerId) {
        if (ownerId == null) {
            throw new BusinessException("INVALID_REQUEST", "Owner ID is required");
        }

        String cacheKey = PRODUCTS_WITH_STOCK_CACHE_PREFIX + ownerId;

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<ProductWithStockResponse> cachedProducts = objectMapper.readValue(
                        cached,
                        new TypeReference<List<ProductWithStockResponse>>() {
                        });
                log.debug("Returning cached products with stock for owner {}", ownerId);
                return cachedProducts;
            }
        } catch (Exception e) {
            log.warn("Error reading from cache for key: {}", cacheKey, e);
        }

        List<Product> products = new ArrayList<>(productRepository.findByOwnerId(ownerId).stream()
                .filter(p -> p.getIsActive() != null && p.getIsActive())
                .toList());
        products = deduplicateByName(products, Product::getName, Product::getPrice, Product::getCreatedAt);

        Map<UUID, BigDecimal> stockByProductId = stockByProductId(ownerId, products);
        List<ProductWithStockResponse> responses = products.stream()
                .map(product -> toWithStock(product, stockByProductId.getOrDefault(product.getId(), BigDecimal.ZERO)))
                .sorted((p1, p2) -> {
                    int stockCompare = p2.getStock().compareTo(p1.getStock());
                    if (stockCompare != 0) {
                        return stockCompare;
                    }
                    return p1.getPrice().compareTo(p2.getPrice());
                })
                .toList();

        try {
            String jsonResponse = objectMapper.writeValueAsString(responses);
            redisTemplate.opsForValue().set(cacheKey, jsonResponse, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached products with stock for owner {}", ownerId);
        } catch (Exception e) {
            log.warn("Error caching products with stock for key: {}", cacheKey, e);
        }

        return responses;
    }

    private BigDecimal formatStock(BigDecimal stock) {
        if (stock == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal stripped = stock.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return stripped.setScale(0);
        }
        return stripped;
    }






    public void invalidateOwnerCaches(UUID ownerId) {
        try {
            redisTemplate.delete(PRODUCTS_CACHE_PREFIX + ownerId);
            redisTemplate.delete(PRODUCTS_WITH_STOCK_CACHE_PREFIX + ownerId);
            log.debug("Invalidated product caches for owner {}", ownerId);
        } catch (Exception e) {
            log.warn("Error invalidating product cache for owner {}: {}", ownerId, e.getMessage());
        }
    }


    private Map<UUID, BigDecimal> stockByProductId(UUID ownerId, List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        return inventoryRepository.findByOwnerIdAndProductIdIn(ownerId, productIds).stream()
                .collect(Collectors.toMap(
                        Inventory::getProductId,
                        inventory -> formatStock(inventory.getStock()),
                        (first, ignored) -> first));
    }

    private ProductWithStockResponse toWithStock(Product product, BigDecimal stock) {
        ProductWithStockResponse response = new ProductWithStockResponse();
        response.setId(product.getId());
        response.setCode(product.getCode());
        response.setName(product.getName());
        response.setPrice(product.getPrice());
        response.setOwnerId(product.getOwnerId());
        response.setIsActive(product.getIsActive());
        response.setStock(formatStock(stock));
        response.setImageUrl(product.getImageUrl());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }


    private <T> List<T> deduplicateByName(List<T> products,
            Function<T, String> nameExtractor,
            Function<T, BigDecimal> priceExtractor,
            Function<T, Instant> createdAtExtractor) {
        Map<String, T> seen = new LinkedHashMap<>();
        for (T product : products) {
            String key = nameExtractor.apply(product).trim().toLowerCase();
            T existing = seen.get(key);
            if (existing == null) {
                seen.put(key, product);
            } else {
                int cmp = priceExtractor.apply(product).compareTo(priceExtractor.apply(existing));
                if (cmp > 0 || (cmp == 0 && createdAtExtractor.apply(product).isBefore(createdAtExtractor.apply(existing)))) {
                    seen.put(key, product);
                }
            }
        }
        return new ArrayList<>(seen.values());
    }

    @Override
    public void evictOwnerCaches(UUID ownerId) {
        try {
            redisTemplate.delete(PRODUCTS_CACHE_PREFIX + ownerId);
            redisTemplate.delete(PRODUCTS_WITH_STOCK_CACHE_PREFIX + ownerId);
        } catch (Exception e) {
            log.warn("Error invalidating product cache for owner {}: {}", ownerId, e.getMessage());
        }
        inventoryService.invalidateOwnerCaches(ownerId);
    }
}
