package com.abhedyam.service;

import com.abhedyam.dto.AnalyticsRequest;
import com.abhedyam.dto.AnalyticsResponse;
import com.abhedyam.dto.DashboardStatsResponse;
import com.abhedyam.dto.RecentActivityResponse;
import com.abhedyam.dto.StatsRequest;
import com.abhedyam.dto.StatsResponse;
import com.abhedyam.model.enums.AnalyticsType;
import com.abhedyam.model.Audit;
import com.abhedyam.model.DailyStats;
import com.abhedyam.model.Product;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.TopProduct;
import com.abhedyam.model.enums.AuditAction;
import com.abhedyam.model.enums.AuditType;
import com.abhedyam.repository.AuditRepository;
import com.abhedyam.repository.DailyStatsRepository;
import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.TopProductRepository;
import com.abhedyam.service.interfaces.IStatsService;
import com.abhedyam.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService implements IStatsService {

        private final DailyStatsRepository dailyStatsRepository;
        private final TopProductRepository topProductRepository;
        private final SaleItemRepository saleItemRepository;
        private final ProductRepository productRepository;
        private final AuditRepository auditRepository;
        private final InventoryRepository inventoryRepository;
        private final RedisTemplate<String, String> redisTemplate;
        private final ObjectMapper objectMapper;

        private static final String STATS_CACHE_PREFIX = "stats:daily:";
        private static final String DASHBOARD_STATS_CACHE_PREFIX = "stats:dashboard:";
        private static final int CACHE_TTL_MINUTES = 5;

        @Override
        @Transactional
        public void aggregateDailyStats(LocalDate date) {
                List<UUID> ownerIds = saleItemRepository.findAll().stream()
                                .map(SaleItem::getOwnerId)
                                .distinct()
                                .toList();

                for (UUID ownerId : ownerIds) {
                        aggregateDailyStatsForOwner(ownerId, date);
                }

                log.info("Daily stats aggregated for date: {}", date);
        }

        @Override
        public List<StatsResponse> getStats(StatsRequest request) {
                UUID ownerId = SecurityUtil.getCurrentUserId();
                LocalDate startDate = request.getStartDate() != null ? request.getStartDate()
                                : LocalDate.now().minusDays(30);
                LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now();

                List<StatsResponse> responses = new ArrayList<>();
                LocalDate currentDate = startDate;

                while (!currentDate.isAfter(endDate)) {
                        final LocalDate dateForLambda = currentDate;
                        String cacheKey = STATS_CACHE_PREFIX + ownerId + ":" + dateForLambda;

                        DailyStats dailyStats = dailyStatsRepository.findByOwnerIdAndStatDate(ownerId, dateForLambda)
                                        .orElseGet(() -> {
                                                String cached = redisTemplate.opsForValue().get(cacheKey);
                                                if (cached == null) {
                                                        aggregateDailyStatsForOwner(ownerId, dateForLambda);
                                                        redisTemplate.opsForValue().set(cacheKey, "cached",
                                                                        CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                                                }
                                                return dailyStatsRepository
                                                                .findByOwnerIdAndStatDate(ownerId, dateForLambda)
                                                                .orElse(null);
                                        });

                        if (dailyStats != null) {
                                List<TopProduct> topProducts = topProductRepository
                                                .findByOwnerIdAndStatDateOrderByRankAsc(ownerId, dateForLambda);

                                List<StatsResponse.TopProductStats> topProductStats = topProducts.stream()
                                                .limit(request.getTopProductsLimit())
                                                .map(tp -> new StatsResponse.TopProductStats(
                                                                tp.getProductId().toString(),
                                                                tp.getProductName(),
                                                                tp.getTotalSales(),
                                                                tp.getTotalQuantity(),
                                                                tp.getOrderCount(),
                                                                tp.getRank()))
                                                .toList();

                                StatsResponse response = new StatsResponse(
                                                dailyStats.getStatDate(),
                                                dailyStats.getTotalSales(),
                                                dailyStats.getTotalOrders(),
                                                dailyStats.getTotalCustomers(),
                                                dailyStats.getTotalProductsSold(),
                                                topProductStats);

                                responses.add(response);
                        }

                        currentDate = currentDate.plusDays(1);
                }

                return responses;
        }

        @Override
        @Transactional
        public void recomputeStats(LocalDate startDate, LocalDate endDate) {
                UUID ownerId = SecurityUtil.getCurrentUserId();
                LocalDate currentDate = startDate;

                while (!currentDate.isAfter(endDate)) {
                        String cacheKey = STATS_CACHE_PREFIX + ownerId + ":" + currentDate;
                        redisTemplate.delete(cacheKey);

                        dailyStatsRepository.findByOwnerIdAndStatDate(ownerId, currentDate)
                                        .ifPresent(dailyStatsRepository::delete);

                        topProductRepository.deleteByOwnerIdAndStatDate(ownerId, currentDate);

                        aggregateDailyStatsForOwner(ownerId, currentDate);

                        currentDate = currentDate.plusDays(1);
                }

                log.info("Stats recomputed for owner {} from {} to {}", ownerId, startDate, endDate);
        }

        @Transactional
        private void aggregateDailyStatsForOwner(UUID ownerId, LocalDate date) {
                Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
                Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

                List<SaleItem> saleItems = saleItemRepository.searchSales(
                                ownerId, null, null, startOfDay, endOfDay, Pageable.unpaged()).getContent().stream()
                                .filter(item -> item.getIsActive() != null && item.getIsActive())
                                .toList();

                if (saleItems.isEmpty()) {
                        return;
                }

                List<String> transactionIds = saleItems.stream()
                                .map(SaleItem::getTransactionId)
                                .filter(tid -> tid != null)
                                .distinct()
                                .toList();

                int totalOrders = transactionIds.size();

                BigDecimal totalSales = saleItems.stream()
                                .map(item -> item.getPrice().multiply(
                                                item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                long totalCustomers = saleItems.stream()
                                .map(SaleItem::getCustomerId)
                                .distinct()
                                .count();

                int totalProductsSold = saleItems.stream()
                                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity().intValue() : 1)
                                .sum();

                DailyStats dailyStats = dailyStatsRepository.findByOwnerIdAndStatDate(ownerId, date)
                                .orElse(new DailyStats());

                dailyStats.setOwnerId(ownerId);
                dailyStats.setStatDate(date);
                dailyStats.setTotalSales(totalSales);
                dailyStats.setTotalOrders(totalOrders);
                dailyStats.setTotalCustomers((int) totalCustomers);
                dailyStats.setTotalProductsSold(totalProductsSold);

                dailyStatsRepository.save(dailyStats);

                Map<UUID, List<SaleItem>> productGroups = saleItems.stream()
                                .collect(Collectors.groupingBy(SaleItem::getProductId));

                List<TopProduct> topProducts = new ArrayList<>();
                int rank = 1;

                List<UUID> productIds = new ArrayList<>(productGroups.keySet());
                List<Product> products = productRepository.findByIdIn(productIds);
                Map<UUID, Product> productMap = products.stream()
                                .collect(Collectors.toMap(Product::getId, p -> p));

                List<Map.Entry<UUID, List<SaleItem>>> sortedProducts = productGroups.entrySet().stream()
                                .sorted((e1, e2) -> {
                                        BigDecimal sales1 = e1.getValue().stream()
                                                        .map(item -> item.getPrice()
                                                                        .multiply(item.getQuantity() != null
                                                                                        ? item.getQuantity()
                                                                                        : BigDecimal.ONE))
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        BigDecimal sales2 = e2.getValue().stream()
                                                        .map(item -> item.getPrice()
                                                                        .multiply(item.getQuantity() != null
                                                                                        ? item.getQuantity()
                                                                                        : BigDecimal.ONE))
                                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                                        return sales2.compareTo(sales1);
                                })
                                .limit(20)
                                .toList();

                for (Map.Entry<UUID, List<SaleItem>> entry : sortedProducts) {
                        UUID productId = entry.getKey();
                        List<SaleItem> items = entry.getValue();

                        Product product = productMap.get(productId);
                        if (product == null) {
                                continue;
                        }

                        BigDecimal productSales = items.stream()
                                        .map(item -> item.getPrice()
                                                        .multiply(item.getQuantity() != null ? item.getQuantity()
                                                                        : BigDecimal.ONE))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        BigDecimal productQuantity = items.stream()
                                        .map(item -> item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        int orderCount = (int) items.stream()
                                        .map(SaleItem::getTransactionId)
                                        .distinct()
                                        .count();

                        TopProduct topProduct = new TopProduct();
                        topProduct.setOwnerId(ownerId);
                        topProduct.setStatDate(date);
                        topProduct.setProductId(productId);
                        topProduct.setProductName(product.getName());
                        topProduct.setTotalSales(productSales);
                        topProduct.setTotalQuantity(productQuantity);
                        topProduct.setOrderCount(orderCount);
                        topProduct.setRank(rank++);

                        topProducts.add(topProduct);
                }

                topProductRepository.saveAll(topProducts);
        }

        @Override
        @Transactional(readOnly = true)
        public DashboardStatsResponse getDashboardStats() {
                UUID ownerId = SecurityUtil.getCurrentUserId();
                String cacheKey = DASHBOARD_STATS_CACHE_PREFIX + ownerId;

                try {
                        String cached = redisTemplate.opsForValue().get(cacheKey);
                        if (cached != null) {
                                DashboardStatsResponse cachedResponse = objectMapper.readValue(
                                                cached,
                                                DashboardStatsResponse.class);
                                log.debug("Returning cached dashboard stats for owner {}", ownerId);
                                return cachedResponse;
                        }
                } catch (Exception e) {
                        log.warn("Error reading from cache for key: {}", cacheKey, e);
                }

                List<Product> products = productRepository.searchProducts(ownerId, null, true, Pageable.unpaged())
                                .getContent();

                if (products.isEmpty()) {
                        return new DashboardStatsResponse(
                                        BigDecimal.ZERO,
                                        0L,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO);
                }

                List<UUID> productIds = products.stream().map(Product::getId).toList();
                List<com.abhedyam.model.Inventory> inventories = inventoryRepository.findByOwnerIdAndProductIdIn(
                                ownerId,
                                productIds);
                java.util.Map<UUID, BigDecimal> stockMap = inventories.stream()
                                .collect(java.util.stream.Collectors.toMap(
                                                com.abhedyam.model.Inventory::getProductId,
                                                inv -> inv.getStock() != null ? inv.getStock() : BigDecimal.ZERO,
                                                (v1, v2) -> v1));

                BigDecimal totalStockCount = BigDecimal.ZERO;
                long lowStockCount = 0;

                for (Product product : products) {
                        BigDecimal stock = stockMap.getOrDefault(product.getId(), BigDecimal.ZERO);
                        totalStockCount = totalStockCount.add(stock);
                        if (stock.compareTo(new BigDecimal("2")) < 0) {
                                lowStockCount++;
                        }
                }

                totalStockCount = totalStockCount.setScale(1, java.math.RoundingMode.HALF_UP);
                BigDecimal stripped = totalStockCount.stripTrailingZeros();
                if (stripped.scale() == 0) {
                        totalStockCount = BigDecimal.valueOf(stripped.intValue());
                } else {
                        totalStockCount = stripped;
                }

                Instant now = Instant.now();
                Instant sevenDaysAgo = now.minusSeconds(7 * 24 * 60 * 60);
                Instant fourteenDaysAgo = now.minusSeconds(14 * 24 * 60 * 60);
                // Only the last two weeks from the DB (was every sale line ever, loaded twice).
                List<SaleItem> recentSales = saleItemRepository.findByOwnerIdAndCreatedAtAfter(ownerId, fourteenDaysAgo);

                List<SaleItem> lastWeekSales = recentSales.stream()
                                .filter(item -> {
                                        Instant createdAt = item.getCreatedAt();
                                        return createdAt != null && createdAt.isAfter(sevenDaysAgo)
                                                        && createdAt.isBefore(now)
                                                        && item.getIsActive() != null && item.getIsActive();
                                })
                                .toList();

                BigDecimal lastWeekSalesAmount = lastWeekSales.stream()
                                .map(item -> item.getPrice().multiply(
                                                item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                List<SaleItem> previousWeekSales = recentSales.stream()
                                .filter(item -> {
                                        Instant createdAt = item.getCreatedAt();
                                        return createdAt != null && createdAt.isAfter(fourteenDaysAgo)
                                                        && createdAt.isBefore(sevenDaysAgo)
                                                        && item.getIsActive() != null && item.getIsActive();
                                })
                                .toList();

                int currentWeekProductsSold = lastWeekSales.stream()
                                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity().intValue() : 1)
                                .sum();

                int previousWeekProductsSold = previousWeekSales.stream()
                                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity().intValue() : 1)
                                .sum();

                BigDecimal weeklyGrowthPercentage = BigDecimal.ZERO;
                if (previousWeekProductsSold > 0) {
                        int difference = currentWeekProductsSold - previousWeekProductsSold;
                        weeklyGrowthPercentage = new BigDecimal(difference)
                                        .divide(new BigDecimal(previousWeekProductsSold), 4, RoundingMode.HALF_UP)
                                        .multiply(new BigDecimal("100"));
                } else if (currentWeekProductsSold > 0) {
                        weeklyGrowthPercentage = new BigDecimal("100");
                }

                DashboardStatsResponse response = new DashboardStatsResponse(
                                totalStockCount,
                                lowStockCount,
                                lastWeekSalesAmount,
                                weeklyGrowthPercentage);

                try {
                        String jsonResponse = objectMapper.writeValueAsString(response);
                        redisTemplate.opsForValue().set(cacheKey, jsonResponse, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                        log.debug("Cached dashboard stats for owner {}", ownerId);
                } catch (Exception e) {
                        log.warn("Error caching dashboard stats for key: {}", cacheKey, e);
                }

                return response;
        }

        @Override
        @Transactional(readOnly = true)
        public Page<RecentActivityResponse> getRecentActivities(Pageable pageable) {
                UUID ownerId = SecurityUtil.getCurrentUserId();

                List<AuditType> types = Arrays.asList(
                                AuditType.SALE,
                                AuditType.PRODUCT,
                                AuditType.REMINDER,
                                AuditType.PAYMENT,
                                AuditType.STOCK);
                List<AuditAction> actions = Arrays.asList(
                                AuditAction.CREATE,
                                AuditAction.SALE_ITEM_SOLD,
                                AuditAction.PRODUCT_CREATED,
                                AuditAction.REMINDER_CREATED,
                                AuditAction.UPDATE,
                                AuditAction.PAYMENT_RECEIVED,
                                AuditAction.PRODUCT_UPDATED,
                                AuditAction.PRODUCT_DELETED);

                Page<Audit> audits = auditRepository.findRecentActivities(ownerId, types, actions, pageable);

                List<RecentActivityResponse> activities = audits.getContent().stream()
                                .map(audit -> {
                                        BigDecimal amount = audit.getAmount();
                                        if (amount != null && amount.compareTo(BigDecimal.ZERO) == 0) {
                                                amount = null;
                                        }
                                        return new RecentActivityResponse(
                                                        audit.getId(),
                                                        audit.getType(),
                                                        audit.getAction(),
                                                        audit.getHeadline(),
                                                        audit.getDescription(),
                                                        amount,
                                                        audit.getEntityId(),
                                                        audit.getCustomerId(),
                                                        audit.getCustomerName(),
                                                        audit.getDetail(),
                                                        audit.getTimestamp());
                                })
                                .collect(Collectors.toList());

                return new PageImpl<>(activities, pageable, audits.getTotalElements());
        }

        @Override
        @Transactional(readOnly = true)
        public AnalyticsResponse getAnalytics(AnalyticsRequest request) {
                UUID ownerId = request.getOwnerId();
                AnalyticsType type = request.getType();

                LocalDate endDate = LocalDate.now();
                LocalDate startDate = endDate;
                List<AnalyticsResponse.PeriodAnalytics> periods = new ArrayList<>();

                if (type == AnalyticsType.DAILY) {
                        startDate = endDate.minusDays(6);
                        List<DailyStats> dailyStatsList = dailyStatsRepository
                                        .findByOwnerIdAndStatDateBetween(ownerId, startDate, endDate);

                        Map<LocalDate, DailyStats> statsMap = dailyStatsList.stream()
                                        .collect(Collectors.toMap(DailyStats::getStatDate, stats -> stats));

                        LocalDate currentDate = startDate;
                        while (!currentDate.isAfter(endDate)) {
                                DailyStats stats = statsMap.get(currentDate);
                                if (stats == null) {
                                        stats = new DailyStats();
                                        stats.setTotalSales(BigDecimal.ZERO);
                                        stats.setTotalOrders(0);
                                        stats.setTotalCustomers(0);
                                        stats.setTotalProductsSold(0);
                                }

                                periods.add(new AnalyticsResponse.PeriodAnalytics(
                                                currentDate.format(DateTimeFormatter.ofPattern("dd/MM")),
                                                currentDate,
                                                currentDate,
                                                stats.getTotalSales(),
                                                stats.getTotalOrders(),
                                                stats.getTotalCustomers(),
                                                stats.getTotalProductsSold()));
                                currentDate = currentDate.plusDays(1);
                        }
                } else if (type == AnalyticsType.WEEKLY) {
                        startDate = endDate.minusWeeks(3); // Last 4 weeks including current
                        // Adjust to ensure we cover 4 weeks ending on endDate, or aligned logic.
                        // "Last 4 weeks" -> Start date is 21 days ago if we count 4 points: [T-21,
                        // T-14, T-7, T]
                        // Or 28 days breakdown?
                        // Let's do 4 blocks of 7 days ending on endDate.
                        startDate = endDate.minusDays(27);

                        LocalDate processingDate = startDate;
                        while (!processingDate.isAfter(endDate)) {
                                LocalDate weekEnd = processingDate.plusDays(6);
                                if (weekEnd.isAfter(endDate))
                                        weekEnd = endDate;
                                final LocalDate finalWeekEnd = weekEnd;
                                final LocalDate finalProcessingDate = processingDate;

                                List<DailyStats> weekStats = dailyStatsRepository
                                                .findByOwnerIdAndStatDateBetween(ownerId, processingDate, weekEnd);

                                BigDecimal totalSales = weekStats.stream()
                                                .map(DailyStats::getTotalSales)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                                int totalOrders = weekStats.stream()
                                                .mapToInt(DailyStats::getTotalOrders)
                                                .sum();

                                int totalCustomers = weekStats.stream()
                                                .mapToInt(DailyStats::getTotalCustomers)
                                                .sum();

                                int totalProductsSold = weekStats.stream()
                                                .mapToInt(DailyStats::getTotalProductsSold)
                                                .sum();

                                periods.add(new AnalyticsResponse.PeriodAnalytics(
                                                finalProcessingDate.format(DateTimeFormatter.ofPattern("MMM dd"))
                                                                + " - "
                                                                + finalWeekEnd.format(
                                                                                DateTimeFormatter.ofPattern("MMM dd")),
                                                finalProcessingDate,
                                                finalWeekEnd,
                                                totalSales,
                                                totalOrders,
                                                totalCustomers,
                                                totalProductsSold));

                                processingDate = processingDate.plusDays(7);
                        }
                } else if (type == AnalyticsType.MONTHLY) {
                        startDate = endDate.minusMonths(5).withDayOfMonth(1);

                        LocalDate processingDate = startDate;
                        while (!processingDate.isAfter(endDate)) {
                                LocalDate monthEnd = processingDate.withDayOfMonth(processingDate.lengthOfMonth());
                                if (monthEnd.isAfter(endDate))
                                        monthEnd = endDate;
                                final LocalDate finalMonthEnd = monthEnd;
                                final LocalDate finalProcessingDate = processingDate;

                                List<DailyStats> monthStats = dailyStatsRepository
                                                .findByOwnerIdAndStatDateBetween(ownerId, processingDate, monthEnd);

                                BigDecimal totalSales = monthStats.stream()
                                                .map(DailyStats::getTotalSales)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                                int totalOrders = monthStats.stream()
                                                .mapToInt(DailyStats::getTotalOrders)
                                                .sum();

                                int totalCustomers = monthStats.stream()
                                                .mapToInt(DailyStats::getTotalCustomers)
                                                .sum();

                                int totalProductsSold = monthStats.stream()
                                                .mapToInt(DailyStats::getTotalProductsSold)
                                                .sum();

                                periods.add(new AnalyticsResponse.PeriodAnalytics(
                                                finalProcessingDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                                finalProcessingDate,
                                                finalMonthEnd,
                                                totalSales,
                                                totalOrders,
                                                totalCustomers,
                                                totalProductsSold));

                                processingDate = processingDate.plusMonths(1);
                        }
                }

                Collections.reverse(periods);
                return new AnalyticsResponse(type, startDate, endDate, periods);
        }

        public void invalidateOwnerCaches(UUID ownerId) {
                try {
                        redisTemplate.delete(DASHBOARD_STATS_CACHE_PREFIX + ownerId);

                        var dailyStatsKeys = redisTemplate.keys(STATS_CACHE_PREFIX + ownerId + ":*");
                        if (dailyStatsKeys != null && !dailyStatsKeys.isEmpty()) {
                                redisTemplate.delete(dailyStatsKeys);
                                log.debug("Invalidated {} daily stats cache keys for owner {}", dailyStatsKeys.size(),
                                                ownerId);
                        }

                        log.debug("Invalidated stats caches for owner {}", ownerId);
                } catch (Exception e) {
                        log.warn("Error invalidating stats cache for owner {}: {}", ownerId, e.getMessage());
                }
        }
}
