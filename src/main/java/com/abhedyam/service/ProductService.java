package com.abhedyam.service;

import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.service.interfaces.IProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

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

    public void invalidateOwnerCaches(UUID ownerId) {
        try {
            redisTemplate.delete(PRODUCTS_CACHE_PREFIX + ownerId);
            redisTemplate.delete(PRODUCTS_WITH_STOCK_CACHE_PREFIX + ownerId);
            log.debug("Invalidated product caches for owner {}", ownerId);
        } catch (Exception e) {
            log.warn("Error invalidating product cache for owner {}: {}", ownerId, e.getMessage());
        }
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
