package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.ItemListResponse;
import com.abhedyam.dto.ItemRequest;
import com.abhedyam.dto.ItemResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.StockChangeRequest;
import com.abhedyam.dto.StockMovementResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Inventory;
import com.abhedyam.model.Product;
import com.abhedyam.model.StockMovement;
import com.abhedyam.model.enums.StockMovementType;
import com.abhedyam.model.enums.StockUnit;
import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.StockMovementRepository;
import com.abhedyam.service.interfaces.IItemService;
import com.abhedyam.service.interfaces.IProductService;
import com.abhedyam.service.interfaces.IStockService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemService implements IItemService {

    private static final int MAX_NAME = 80;
    static final int NOT_SELLING_DAYS = 60;

    /** Sales of one item: quantity in the last 30 days and the latest sale. */
    record Sales(BigDecimal last30, Instant lastSoldAt) {
        static final Sales NONE = new Sales(BigDecimal.ZERO, null);
    }

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final IStockService stockService;
    private final IProductService productService;
    private final SaleItemRepository saleItems;

    @Override
    @Transactional(readOnly = true)
    public ItemListResponse list(UUID ownerId, Filter filter, String search, int page, int size) {
        requireSelf(ownerId);
        List<ItemResponse> all = all(ownerId);

        BigDecimal value = BigDecimal.ZERO, profit = BigDecimal.ZERO, stuck = BigDecimal.ZERO;
        int missingCost = 0, notSelling = 0;
        for (ItemResponse i : all) {
            if (!i.isTrackStock() || i.getStock().signum() <= 0) continue;
            if (i.getStockValue() == null) missingCost++;
            else {
                value = value.add(i.getStockValue());
                profit = profit.add(i.getStock().multiply(i.getPrice().subtract(i.getCostPrice())));
            }
            if (i.isNotSelling()) {
                notSelling++;
                if (i.getStockValue() != null) stuck = stuck.add(i.getStockValue());
            }
        }
        ItemListResponse.Summary summary = new ItemListResponse.Summary(
                all.size(),
                (int) all.stream().filter(ItemResponse::isTrackStock).count(),
                (int) all.stream().filter(i -> i.getStatus() == ItemResponse.Status.LOW).count(),
                (int) all.stream().filter(i -> i.getStatus() == ItemResponse.Status.OUT).count(),
                value.setScale(2, RoundingMode.HALF_UP),
                missingCost,
                profit.setScale(2, RoundingMode.HALF_UP),
                notSelling,
                stuck.setScale(2, RoundingMode.HALF_UP));

        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<ItemResponse> shown = all.stream()
                .filter(i -> q.isEmpty() || i.getName().toLowerCase(Locale.ROOT).contains(q))
                .filter(i -> filter == null || filter == Filter.ALL
                        || (filter == Filter.LOW && i.getStatus() == ItemResponse.Status.LOW)
                        || (filter == Filter.OUT && i.getStatus() == ItemResponse.Status.OUT)
                        || (filter == Filter.NOT_SELLING && i.isNotSelling()))
                .sorted(Comparator.comparingInt((ItemResponse i) -> rank(i.getStatus()))
                        .thenComparing(i -> i.getName().toLowerCase(Locale.ROOT)))
                .toList();

        int pageSize = Math.max(1, Math.min(size, 100));
        int pageNo = Math.max(0, page);
        int from = Math.min(pageNo * pageSize, shown.size());
        int to = Math.min(from + pageSize, shown.size());
        int pages = (shown.size() + pageSize - 1) / pageSize;
        return new ItemListResponse(summary, new PageResponse<>(shown.subList(from, to), pageNo, pageSize,
                shown.size(), pages, to < shown.size(), pageNo > 0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemResponse> all(UUID ownerId) {
        // ponytail: loads all of an owner's active items and sale lines into memory (shops have hundreds of items, thousands of sale lines); move to SQL aggregates if an owner passes ~50k sale lines.
        List<Product> products = productRepository.findByOwnerId(ownerId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .toList();
        Map<UUID, BigDecimal> stock = inventoryRepository.findByOwnerId(ownerId).stream()
                .filter(i -> i.getStock() != null)
                .collect(Collectors.toMap(Inventory::getProductId, Inventory::getStock, (a, b) -> a));
        Map<UUID, Sales> sales = sales(ownerId, Instant.now());
        Instant now = Instant.now();
        return products.stream()
                .map(p -> toResponse(p, stock.get(p.getId()), sales.getOrDefault(p.getId(), Sales.NONE), now))
                .toList();
    }

    private Map<UUID, Sales> sales(UUID ownerId, Instant now) {
        Instant since = now.minus(Duration.ofDays(30));
        Map<UUID, BigDecimal> qty = new HashMap<>();
        Map<UUID, Instant> last = new HashMap<>();
        for (Object[] row : saleItems.saleLinesByOwner(ownerId)) {
            UUID pid = row[0] instanceof UUID u ? u : UUID.fromString(row[0].toString());
            BigDecimal q = row[1] == null ? BigDecimal.ONE : new BigDecimal(row[1].toString());
            Instant at = (Instant) row[2];
            if (at == null) continue;
            last.merge(pid, at, (a, b) -> a.isAfter(b) ? a : b);
            if (!at.isBefore(since)) qty.merge(pid, q, BigDecimal::add);
        }
        Map<UUID, Sales> out = new HashMap<>();
        last.forEach((pid, at) -> out.put(pid, new Sales(qty.getOrDefault(pid, BigDecimal.ZERO), at)));
        return out;
    }

    @Override
    @Transactional
    public ItemResponse create(UUID ownerId, ItemRequest request) {
        requireSelf(ownerId);
        String name = checkName(request.getName());
        requireUniqueName(ownerId, name, null);
        if (request.getPrice() == null || request.getPrice().signum() < 0) throw invalid("Price must be ₹0 or more");

        Product p = new Product();
        p.setOwnerId(ownerId);
        p.setName(name);
        p.setCode(newCode(ownerId));
        p.setPrice(request.getPrice().setScale(2, RoundingMode.HALF_UP));
        p.setIsActive(true);
        p.setUnit(request.getUnit() == null ? StockUnit.PCS : request.getUnit());
        p.setCostPrice(costPrice(request.getCostPrice()));
        p.setLowStockLevel(lowLevel(request.getLowStockLevel()));
        p.setImageUrl(blankToNull(request.getImageUrl()));
        p.setTrackStock(Boolean.TRUE.equals(request.getTrackStock()) || request.getOpeningStock() != null);
        p = productRepository.save(p);

        if (request.getOpeningStock() != null) {
            stockService.count(ownerId, p, quantity(request.getOpeningStock(), true), null);
        }
        productService.evictOwnerCaches(ownerId);
        return toResponse(p, stockService.currentStock(ownerId, p.getId()));
    }

    @Override
    @Transactional
    public ItemResponse update(UUID ownerId, UUID itemId, ItemRequest request) {
        requireSelf(ownerId);
        Product p = find(ownerId, itemId);
        if (request.getName() != null) {
            String name = checkName(request.getName());
            requireUniqueName(ownerId, name, p.getId());
            p.setName(name);
        }
        if (request.getPrice() != null) {
            if (request.getPrice().signum() < 0) throw invalid("Price must be ₹0 or more");
            p.setPrice(request.getPrice().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.getCostPrice() != null) p.setCostPrice(costPrice(request.getCostPrice()));
        if (request.getUnit() != null) p.setUnit(request.getUnit());
        if (request.getLowStockLevel() != null) p.setLowStockLevel(lowLevel(request.getLowStockLevel()));
        if (request.getImageUrl() != null) p.setImageUrl(blankToNull(request.getImageUrl()));
        if (request.getTrackStock() != null) p.setTrackStock(request.getTrackStock());
        p = productRepository.save(p);

        if (request.getOpeningStock() != null) {
            stockService.count(ownerId, p, quantity(request.getOpeningStock(), true), null);
        }
        productService.evictOwnerCaches(ownerId);
        return toResponse(p, stockService.currentStock(ownerId, p.getId()));
    }

    @Override
    @Transactional
    public void archive(UUID ownerId, UUID itemId) {
        requireSelf(ownerId);
        Product p = find(ownerId, itemId);
        p.setIsActive(false);
        productRepository.save(p);
        productService.evictOwnerCaches(ownerId);
    }

    @Override
    @Transactional
    public ItemResponse changeStock(UUID ownerId, UUID itemId, StockChangeRequest request) {
        requireSelf(ownerId);
        Product p = find(ownerId, itemId);
        if (request.getType() == null) throw invalid("Say if stock was received, removed or counted");
        if (request.getType() == StockChangeRequest.Type.COUNT) {
            stockService.count(ownerId, p, quantity(request.getQuantity(), true), request.getNote());
        } else {
            BigDecimal q = quantity(request.getQuantity(), false);
            if (!p.tracksStock()) {
                p.setTrackStock(true);
                productRepository.save(p);
            }
            boolean received = request.getType() == StockChangeRequest.Type.RECEIVED;
            stockService.apply(ownerId, p, received ? StockMovementType.RECEIVED : StockMovementType.REMOVED,
                    received ? q : q.negate(), request.getReason(), request.getNote(), null);
        }
        productService.evictOwnerCaches(ownerId);
        return toResponse(p, stockService.currentStock(ownerId, p.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(UUID ownerId, UUID itemId, int page, int size) {
        requireSelf(ownerId);
        find(ownerId, itemId);
        Page<StockMovement> result = movementRepository.findByOwnerIdAndProductIdOrderByCreatedAtDesc(
                ownerId, itemId, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
        return new PageResponse<>(result.getContent().stream().map(ItemService::toMovement).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.hasNext(), result.hasPrevious());
    }

    private ItemResponse toResponse(Product p, BigDecimal rawStock) {
        Map<UUID, Sales> sales = sales(p.getOwnerId(), Instant.now());
        return toResponse(p, rawStock, sales.getOrDefault(p.getId(), Sales.NONE), Instant.now());
    }

    static ItemResponse toResponse(Product p, BigDecimal rawStock, Sales sales, Instant now) {
        boolean tracked = p.tracksStock();
        BigDecimal stock = tracked ? (rawStock == null ? BigDecimal.ZERO : rawStock).stripTrailingZeros() : null;
        ItemResponse.Status status;
        if (!tracked) status = ItemResponse.Status.UNTRACKED;
        else if (stock.signum() <= 0) status = ItemResponse.Status.OUT;
        else if (p.getLowStockLevel() != null && stock.compareTo(p.getLowStockLevel()) <= 0) status = ItemResponse.Status.LOW;
        else status = ItemResponse.Status.OK;
        BigDecimal value = tracked && stock.signum() > 0 && p.getCostPrice() != null
                ? stock.multiply(p.getCostPrice()).setScale(2, RoundingMode.HALF_UP) : null;
        Integer daysLeft = null;
        if (tracked && stock.signum() <= 0) daysLeft = 0;
        else if (tracked && sales.last30().signum() > 0) {
            daysLeft = stock.multiply(BigDecimal.valueOf(30)).divide(sales.last30(), 0, RoundingMode.DOWN).intValue();
        }
        Instant cutoff = now.minus(Duration.ofDays(NOT_SELLING_DAYS));
        Instant since = sales.lastSoldAt() != null ? sales.lastSoldAt() : p.getCreatedAt();
        boolean notSelling = tracked && stock.signum() > 0 && since != null && since.isBefore(cutoff);
        return new ItemResponse(p.getId(), p.getName(), p.getPrice(), p.getCostPrice(),
                p.getUnit() == null ? StockUnit.PCS : p.getUnit(), tracked, stock,
                p.getLowStockLevel() == null ? null : p.getLowStockLevel().stripTrailingZeros(),
                status, value, p.getImageUrl(), p.getUpdatedAt(),
                sales.last30().stripTrailingZeros(), sales.lastSoldAt(), daysLeft, notSelling);
    }

    private static StockMovementResponse toMovement(StockMovement m) {
        return new StockMovementResponse(m.getId(), m.getType(), m.getQuantity().stripTrailingZeros(),
                m.getStockAfter().stripTrailingZeros(), m.getReason(), m.getNote(), m.getSaleTransactionId(), m.getCreatedAt());
    }

    private static int rank(ItemResponse.Status s) {
        return switch (s) {
            case OUT -> 0;
            case LOW -> 1;
            default -> 2;
        };
    }

    private Product find(UUID ownerId, UUID itemId) {
        return productRepository.findById(itemId)
                .filter(p -> ownerId.equals(p.getOwnerId()) && Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Item could not be found"));
    }

    private void requireUniqueName(UUID ownerId, String name, UUID self) {
        boolean taken = productRepository.findByOwnerIdAndName(ownerId, name).stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getIsActive()) && !p.getId().equals(self));
        if (taken) throw new BusinessException(ErrorCodes.ITEM_EXISTS, "You already have an item called " + name);
    }

    private String newCode(UUID ownerId) {
        String code;
        do {
            code = "ITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (productRepository.findByOwnerIdAndCode(ownerId, code).isPresent());
        return code;
    }

    private static String checkName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw invalid("Give the item a name");
        if (name.length() > MAX_NAME) throw invalid("Name is too long (max " + MAX_NAME + " letters)");
        return name;
    }

    private static BigDecimal quantity(BigDecimal q, boolean zeroAllowed) {
        if (q == null || q.signum() < 0 || (!zeroAllowed && q.signum() == 0)) {
            throw new BusinessException(ErrorCodes.INVALID_QUANTITY, zeroAllowed ? "Stock must be 0 or more" : "Quantity must be more than 0");
        }
        return q.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal costPrice(BigDecimal c) {
        if (c == null || c.signum() == 0) return null;
        if (c.signum() < 0) throw invalid("Buying price must be ₹0 or more");
        return c.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal lowLevel(BigDecimal l) {
        if (l == null || l.signum() == 0) return null;
        if (l.signum() < 0) throw invalid("Low stock level must be more than 0");
        return l.setScale(3, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCodes.INVALID, message);
    }

    private static void requireSelf(UUID ownerId) {
        if (!ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only see your own items");
        }
    }
}
