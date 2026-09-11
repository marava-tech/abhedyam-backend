package com.abhedyam.service;

import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.ProductCreateRequest;
import com.abhedyam.dto.ProductSearchRequest;
import com.abhedyam.dto.ProductSearchResult;
import com.abhedyam.dto.ProductUpdateRequest;
import com.abhedyam.dto.ProductWithStockResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Inventory;
import com.abhedyam.model.Product;
import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.service.interfaces.IAuditService;
import com.abhedyam.service.interfaces.IProductService;
import com.abhedyam.util.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final IAuditService auditService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    private static final String PRODUCTS_CACHE_PREFIX = "products:owner:";
    private static final String PRODUCTS_WITH_STOCK_CACHE_PREFIX = "products:with-stock:";
    private static final int CACHE_TTL_MINUTES = 5;

    @Override
    @Transactional
    public Product create(ProductCreateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();

        if (productRepository.findByOwnerIdAndCode(ownerId, request.getCode()).isPresent()) {
            throw new BusinessException("DUPLICATE_CODE", "Product code already exists");
        }

        if (!productRepository.findByOwnerIdAndName(ownerId, request.getName().trim()).isEmpty()) {
            throw new BusinessException("DUPLICATE_NAME", "Product with this name already exists");
        }

        Product product = new Product();
        product.setCode(request.getCode());
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setOwnerId(ownerId);
        product.setIsActive(true);
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl().trim().isEmpty() ? null : request.getImageUrl());
        }

        Product savedProduct = productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProductId(savedProduct.getId());
        inventory.setOwnerId(ownerId);
        inventory.setStock(java.math.BigDecimal.ZERO);
        inventoryRepository.save(inventory);

        auditService.logProductCreation(savedProduct.getId(), ownerId, savedProduct.getName(), savedProduct.getCode());

        try {
            redisTemplate.delete(PRODUCTS_CACHE_PREFIX + ownerId);
            redisTemplate.delete(PRODUCTS_WITH_STOCK_CACHE_PREFIX + ownerId);
        } catch (Exception e) {
            log.warn("Error invalidating product cache on create: {}", e.getMessage());
        }

        inventoryService.invalidateOwnerCaches(ownerId);

        return savedProduct;
    }

    @Override
    @Transactional(readOnly = true)
    public Product getById(UUID id) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product could not be found"));

        if (!product.getOwnerId().equals(ownerId)) {
            throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this product");
        }

        return product;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getByOwnerId(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        if (ownerId != null && !currentOwnerId.equals(ownerId)) {
            throw new BusinessException("UNAUTHORIZED", "You can only access your own products");
        }
        UUID targetOwnerId = ownerId != null ? ownerId : currentOwnerId;
        String cacheKey = PRODUCTS_CACHE_PREFIX + targetOwnerId;

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<Product> cachedProducts = objectMapper.readValue(
                        cached,
                        new TypeReference<List<Product>>() {
                        });
                log.debug("Returning cached products for owner {}", targetOwnerId);
                return cachedProducts;
            }
        } catch (Exception e) {
            log.warn("Error reading from cache for key: {}", cacheKey, e);
        }

        List<Product> products = productRepository.findByOwnerId(targetOwnerId);

        try {
            String jsonResponse = objectMapper.writeValueAsString(products);
            redisTemplate.opsForValue().set(cacheKey, jsonResponse, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached products for owner {}", targetOwnerId);
        } catch (Exception e) {
            log.warn("Error caching products for key: {}", cacheKey, e);
        }

        return products;
    }

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductWithStockResponse> searchProducts(ProductSearchRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        return searchProductsInternal(ownerId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductWithStockResponse> searchProductsByOwner(UUID ownerId, ProductSearchRequest request) {
        validateOwnerAccess(ownerId);
        return searchProductsInternal(ownerId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSearchResult> searchByName(String name) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        List<Product> products = productRepository.findByNameContainingIgnoreCaseAndOwnerId(name, ownerId);
        return products.stream()
                .map(product -> new ProductSearchResult(product.getId(), product.getName(), product.getPrice()))
                .toList();
    }

    @Override
    @Transactional
    public Product updateProduct(ProductUpdateRequest request) {
        Product product = getById(request.getId());

        if (request.getCode() != null && !request.getCode().equals(product.getCode())) {
            UUID ownerId = SecurityUtil.getCurrentUserId();
            if (productRepository.findByOwnerIdAndCode(ownerId, request.getCode()).isPresent()) {
                throw new BusinessException("DUPLICATE_CODE", "Product code already exists");
            }
            product.setCode(request.getCode());
        }
        if (request.getName() != null && !request.getName().trim().isEmpty()
                && !request.getName().trim().equals(product.getName())) {
            UUID ownerId = SecurityUtil.getCurrentUserId();
            if (!productRepository.findByOwnerIdAndName(ownerId, request.getName().trim()).isEmpty()) {
                throw new BusinessException("DUPLICATE_NAME", "Product with this name already exists");
            }
            product.setName(request.getName().trim());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getImageUrl() != null) {
            if (request.getImageUrl().trim().isEmpty()) {
                product.setImageUrl(null);
            } else {
                product.setImageUrl(request.getImageUrl());
            }
        }

        Product updatedProduct = productRepository.save(product);
        UUID ownerId = updatedProduct.getOwnerId();

        try {
            redisTemplate.delete(PRODUCTS_CACHE_PREFIX + ownerId);
            redisTemplate.delete(PRODUCTS_WITH_STOCK_CACHE_PREFIX + ownerId);
        } catch (Exception e) {
            log.warn("Error invalidating product cache on update: {}", e.getMessage());
        }

        return updatedProduct;
    }

    @Override
    @Transactional
    public Product toggleActive(UUID id) {
        Product product = getById(id);
        product.setIsActive(!product.getIsActive());
        Product updatedProduct = productRepository.save(product);
        UUID ownerId = updatedProduct.getOwnerId();

        try {
            redisTemplate.delete(PRODUCTS_CACHE_PREFIX + ownerId);
            redisTemplate.delete(PRODUCTS_WITH_STOCK_CACHE_PREFIX + ownerId);
        } catch (Exception e) {
            log.warn("Error invalidating product cache on toggle: {}", e.getMessage());
        }

        return updatedProduct;
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

    private PageResponse<ProductWithStockResponse> searchProductsInternal(UUID ownerId, ProductSearchRequest request) {
        String searchTerm = request.getSearchTerm();
        if (searchTerm != null && searchTerm.trim().isEmpty()) {
            searchTerm = null;
        }
        Integer page = request.getPage();
        Integer size = request.getSize();
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size < 1) {
            size = 20;
        }

        Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(request.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                request.getSortBy());

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> productPage = productRepository.searchProducts(
                ownerId,
                searchTerm,
                request.getIsActive(),
                pageable);

        List<Product> deduped = deduplicateByName(
                productPage.getContent(),
                Product::getName,
                Product::getPrice,
                Product::getCreatedAt);

        Map<UUID, BigDecimal> stockByProductId = stockByProductId(ownerId, deduped);
        List<ProductWithStockResponse> content = deduped.stream()
                .map(product -> toWithStock(product, stockByProductId.getOrDefault(product.getId(), BigDecimal.ZERO)))
                .toList();

        return new PageResponse<>(
                content,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.hasNext(),
                productPage.hasPrevious());
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

    private void validateOwnerAccess(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        if (ownerId == null || !ownerId.equals(currentOwnerId)) {
            throw new BusinessException("UNAUTHORIZED", "You can only access your own products");
        }
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
}
