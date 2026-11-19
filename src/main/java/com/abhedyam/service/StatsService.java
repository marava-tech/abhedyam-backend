package com.abhedyam.service;

import com.abhedyam.dto.AnalyticsRequest;
import com.abhedyam.dto.AnalyticsResponse;
import com.abhedyam.dto.DashboardStatsResponse;
import com.abhedyam.dto.RecentActivityResponse;
import com.abhedyam.model.enums.AnalyticsType;
import com.abhedyam.model.Audit;
import com.abhedyam.model.Product;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.AuditAction;
import com.abhedyam.model.enums.AuditType;
import com.abhedyam.repository.AuditRepository;
import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
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

        private final SaleItemRepository saleItemRepository;
        private final com.abhedyam.service.interfaces.INotificationSettingsService notificationSettings;
        private final ProductRepository productRepository;
        private final AuditRepository auditRepository;
        private final com.abhedyam.repository.ShopMemberRepository memberRepository;
        private final InventoryRepository inventoryRepository;
        private final RedisTemplate<String, String> redisTemplate;
        private final ObjectMapper objectMapper;

        private static final String DASHBOARD_STATS_CACHE_PREFIX = "stats:dashboard:";
        private static final int CACHE_TTL_MINUTES = 5;

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

                java.util.Map<UUID, String> memberNames = new java.util.HashMap<>();
                memberRepository.findByOwnerId(ownerId).forEach(m -> memberNames.put(m.getId(), m.getName()));
                List<RecentActivityResponse> activities = audits.getContent().stream()
                                .map(audit -> {
                                        BigDecimal amount = audit.getAmount();
                                        if (amount != null && amount.compareTo(BigDecimal.ZERO) == 0) {
                                                amount = null;
                                        }
                                        RecentActivityResponse r = new RecentActivityResponse(
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
                                        if (audit.getAddedBy() != null) r.setAddedBy(memberNames.get(audit.getAddedBy()));
                                        r.setFacts(ActivityText.read(audit.getFacts()));
                                        return r;
                                })
                                .collect(Collectors.toList());

                return new PageImpl<>(activities, pageable, audits.getTotalElements());
        }

        @Override
        @Transactional(readOnly = true)
        public AnalyticsResponse getAnalytics(AnalyticsRequest request) {
                UUID ownerId = request.getOwnerId();
                AnalyticsType type = request.getType();
                ZoneId zone = ZoneId.of(notificationSettings.forOwner(ownerId).getTimezone());
                LocalDate endDate = LocalDate.now(zone);
                List<AnalyticsResponse.PeriodAnalytics> periods = new ArrayList<>();
                LocalDate startDate;

                // Straight from the sales, so today's sales (and edits, cancels, sales sent late from an offline
                // phone) show at once; daily_stats is only filled the night after.
                if (type == AnalyticsType.DAILY) {
                        startDate = endDate.minusDays(6);
                        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                                periods.add(period(ownerId, zone, d.format(DateTimeFormatter.ofPattern("dd/MM")), d, d));
                        }
                } else if (type == AnalyticsType.WEEKLY) {
                        startDate = endDate.minusDays(27); // 4 blocks of 7 days ending today
                        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(7)) {
                                LocalDate weekEnd = d.plusDays(6).isAfter(endDate) ? endDate : d.plusDays(6);
                                DateTimeFormatter f = DateTimeFormatter.ofPattern("MMM dd");
                                periods.add(period(ownerId, zone, d.format(f) + " - " + weekEnd.format(f), d, weekEnd));
                        }
                } else {
                        startDate = endDate.minusMonths(5).withDayOfMonth(1);
                        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusMonths(1)) {
                                LocalDate monthEnd = d.withDayOfMonth(d.lengthOfMonth()).isAfter(endDate)
                                                ? endDate : d.withDayOfMonth(d.lengthOfMonth());
                                periods.add(period(ownerId, zone, d.format(DateTimeFormatter.ofPattern("MMMM yyyy")), d, monthEnd));
                        }
                }

                Collections.reverse(periods);
                return new AnalyticsResponse(type, startDate, endDate, periods);
        }

        /** Every active sale from the first day to the last day (both whole days, owner's time zone). */
        private AnalyticsResponse.PeriodAnalytics period(UUID ownerId, ZoneId zone, String label, LocalDate from, LocalDate to) {
                Object[] t = saleItemRepository.periodTotals(ownerId, from.atStartOfDay(zone).toInstant(),
                                to.plusDays(1).atStartOfDay(zone).toInstant()).get(0);
                return new AnalyticsResponse.PeriodAnalytics(label, from, to, (BigDecimal) t[0],
                                ((Number) t[1]).intValue(), ((Number) t[2]).intValue(), ((Number) t[3]).intValue());
        }

        public void invalidateOwnerCaches(UUID ownerId) {
                try {
                        redisTemplate.delete(DASHBOARD_STATS_CACHE_PREFIX + ownerId);

                        log.debug("Invalidated stats caches for owner {}", ownerId);
                } catch (Exception e) {
                        log.warn("Error invalidating stats cache for owner {}: {}", ownerId, e.getMessage());
                }
        }
}
