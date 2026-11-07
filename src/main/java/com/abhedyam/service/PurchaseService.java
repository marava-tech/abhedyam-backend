package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.ItemRequest;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.dto.PurchaseRequest;
import com.abhedyam.dto.PurchaseResponse;
import com.abhedyam.dto.ItemResponse;
import com.abhedyam.dto.ToBuyLine;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Product;
import com.abhedyam.model.StockMovement;
import com.abhedyam.model.StockPurchase;
import com.abhedyam.model.StockPurchaseItem;
import com.abhedyam.model.enums.StockMovementType;
import com.abhedyam.model.enums.StockRemovalReason;
import com.abhedyam.model.enums.StockUnit;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.StockMovementRepository;
import com.abhedyam.repository.StockPurchaseItemRepository;
import com.abhedyam.repository.StockPurchaseRepository;
import com.abhedyam.service.interfaces.IItemService;
import com.abhedyam.service.interfaces.IProductService;
import com.abhedyam.service.interfaces.IPurchaseService;
import com.abhedyam.service.interfaces.IStockService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stock bought in one go (specs/stock-purchases.md). */
@Service
@RequiredArgsConstructor
public class PurchaseService implements IPurchaseService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final StockPurchaseRepository purchases;
    private final StockPurchaseItemRepository lines;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final IStockService stock;
    private final IItemService items;
    private final IProductService productService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PurchaseResponse> list(UUID ownerId, Status status, UUID itemId, int page, int size) {
        requireSelf(ownerId);
        // ponytail: builds every purchase of the owner in memory (a shop buys a few times a week); page in SQL past ~5k purchases.
        List<PurchaseResponse> all = build(ownerId, purchases.findByOwnerIdOrderByBoughtOnDescCreatedAtDesc(ownerId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive())).toList());
        List<PurchaseResponse> shown = all.stream()
                .filter(p -> status != Status.OPEN || !p.isFinished())
                .filter(p -> itemId == null || p.getItems().stream().anyMatch(l -> l.getItemId().equals(itemId)))
                .toList();
        int pageSize = Math.max(1, Math.min(size, 100));
        int pageNo = Math.max(0, page);
        int from = Math.min(pageNo * pageSize, shown.size());
        int to = Math.min(from + pageSize, shown.size());
        return new PageResponse<>(shown.subList(from, to), pageNo, pageSize, shown.size(),
                (shown.size() + pageSize - 1) / pageSize, to < shown.size(), pageNo > 0);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseResponse get(UUID ownerId, UUID purchaseId) {
        requireSelf(ownerId);
        return build(ownerId, List.of(find(ownerId, purchaseId))).get(0);
    }

    @Override
    @Transactional
    public PurchaseResponse create(UUID ownerId, PurchaseRequest request) {
        requireSelf(ownerId);
        List<PurchaseRequest.Line> req = request.getItems() == null ? List.of() : request.getItems();
        if (req.isEmpty()) throw invalid("Add at least one item");

        List<Product> bought = new ArrayList<>();
        List<PurchaseMath.Line> math = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (PurchaseRequest.Line l : req) {
            BigDecimal q = l.getQuantity();
            if (q == null || q.signum() <= 0) throw new BusinessException(ErrorCodes.INVALID_QUANTITY, "Every item needs a quantity more than 0");
            if (l.getUnitCost() != null && l.getUnitCost().signum() < 0) throw invalid("Buying price must be ₹0 or more");
            Product p = resolve(ownerId, l);
            if (!seen.add(p.getId())) throw invalid(p.getName() + " is in the list twice");
            bought.add(p);
            math.add(new PurchaseMath.Line(q.setScale(3, RoundingMode.HALF_UP), p.getPrice(), l.getUnitCost()));
        }

        boolean allGiven = math.stream().allMatch(m -> m.given() != null);
        BigDecimal total = request.getTotalPaid();
        if (total == null) {
            if (!allGiven) throw invalid("Type how much you paid for this purchase");
            total = math.stream().map(m -> m.given().multiply(m.quantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (total.signum() < 0) throw invalid("Amount paid must be ₹0 or more");
        total = total.setScale(2, RoundingMode.HALF_UP);
        List<BigDecimal> costs;
        try {
            costs = PurchaseMath.unitCosts(math, total);
        } catch (IllegalArgumentException e) {
            throw invalid("The buying prices you typed add up to more than the amount paid");
        }

        LocalDate today = LocalDate.now(IST);
        LocalDate on = request.getBoughtOn() == null ? today : request.getBoughtOn();
        if (on.isAfter(today)) throw invalid("The purchase date can't be in the future");

        StockPurchase purchase = new StockPurchase();
        purchase.setOwnerId(ownerId);
        purchase.setSupplier(trim(request.getSupplier(), 60));
        purchase.setBoughtOn(on);
        purchase.setTotalPaid(total);
        purchase.setNote(trim(request.getNote(), 200));
        purchase = purchases.save(purchase);

        String note = purchase.getSupplier() == null ? "Purchase" : "Purchase from " + purchase.getSupplier();
        for (int i = 0; i < bought.size(); i++) {
            Product p = bought.get(i);
            StockPurchaseItem line = new StockPurchaseItem();
            line.setPurchaseId(purchase.getId());
            line.setOwnerId(ownerId);
            line.setProductId(p.getId());
            line.setQuantity(math.get(i).quantity());
            line.setUnitCost(costs.get(i));
            line.setCostGiven(math.get(i).given() != null);
            lines.save(line);

            p.setTrackStock(true);
            p.setCostPrice(costs.get(i).setScale(2, RoundingMode.HALF_UP));
            products.save(p);
            stock.apply(ownerId, p, StockMovementType.RECEIVED, math.get(i).quantity(), null, note, null, purchase.getId());
        }
        productService.evictOwnerCaches(ownerId);
        return build(ownerId, List.of(purchase)).get(0);
    }

    @Override
    @Transactional
    public void delete(UUID ownerId, UUID purchaseId) {
        requireSelf(ownerId);
        StockPurchase purchase = find(ownerId, purchaseId);
        PurchaseResponse view = build(ownerId, List.of(purchase)).get(0);
        if (!view.isCanDelete()) {
            throw new BusinessException(ErrorCodes.INVALID, "Some of this purchase is already sold, so it can't be deleted. Use Remove or Count on the item instead.");
        }
        for (StockPurchaseItem line : lines.findByPurchaseIdIn(List.of(purchaseId))) {
            Product p = products.findById(line.getProductId()).orElse(null);
            if (p == null) continue;
            stock.apply(ownerId, p, StockMovementType.REMOVED, line.getQuantity().negate(), StockRemovalReason.OTHER,
                    "Purchase deleted", null, purchaseId);
        }
        purchase.setIsActive(false);
        purchases.save(purchase);
        productService.evictOwnerCaches(ownerId);
    }

    static final int RUNNING_OUT_DAYS = 7;
    static final int BUY_FOR_DAYS = 14;

    @Override
    @Transactional(readOnly = true)
    public List<ToBuyLine> toBuy(UUID ownerId) {
        requireSelf(ownerId);
        Map<UUID, StockPurchaseItem> lastLine = new HashMap<>();
        Map<UUID, StockPurchase> lastPurchase = new HashMap<>();
        List<StockPurchase> active = purchases.findByOwnerIdOrderByBoughtOnDescCreatedAtDesc(ownerId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive())).toList();
        Map<UUID, List<StockPurchaseItem>> linesByPurchase = active.isEmpty() ? Map.of()
                : lines.findByPurchaseIdIn(active.stream().map(StockPurchase::getId).toList()).stream()
                        .collect(Collectors.groupingBy(StockPurchaseItem::getPurchaseId));
        for (StockPurchase p : active) {
            for (StockPurchaseItem l : linesByPurchase.getOrDefault(p.getId(), List.of())) {
                if (lastLine.putIfAbsent(l.getProductId(), l) == null) lastPurchase.put(l.getProductId(), p);
            }
        }
        List<ToBuyLine> out = new ArrayList<>();
        for (ItemResponse i : items.all(ownerId)) {
            if (!i.isTrackStock()) continue;
            ToBuyLine.Reason reason;
            if (i.getStatus() == ItemResponse.Status.OUT) reason = ToBuyLine.Reason.OUT;
            else if (i.getStatus() == ItemResponse.Status.LOW) reason = ToBuyLine.Reason.LOW;
            else if (i.getDaysLeft() != null && i.getDaysLeft() <= RUNNING_OUT_DAYS) reason = ToBuyLine.Reason.RUNNING_OUT;
            else continue;
            StockPurchaseItem last = lastLine.get(i.getId());
            StockPurchase lastP = lastPurchase.get(i.getId());
            out.add(new ToBuyLine(i, reason, suggest(i, last == null ? null : last.getQuantity()),
                    lastP == null ? null : lastP.getSupplier(),
                    last == null ? null : last.getUnitCost().setScale(2, RoundingMode.HALF_UP),
                    last == null ? null : last.getQuantity().stripTrailingZeros(),
                    lastP == null ? null : lastP.getBoughtOn()));
        }
        out.sort(Comparator.comparingInt((ToBuyLine l) -> l.getReason().ordinal())
                .thenComparingInt(l -> l.getItem().getDaysLeft() == null ? Integer.MAX_VALUE : l.getItem().getDaysLeft())
                .thenComparing(l -> l.getItem().getName().toLowerCase()));
        return out;
    }

    /** Two weeks of sales minus what is on the shelf; for out/low items at least what was bought last time. Whole units, at least 1. */
    static BigDecimal suggest(ItemResponse i, BigDecimal lastQuantity) {
        BigDecimal stockNow = i.getStock().max(BigDecimal.ZERO);
        BigDecimal need = i.getSoldLast30().multiply(BigDecimal.valueOf(BUY_FOR_DAYS))
                .divide(BigDecimal.valueOf(30), 3, RoundingMode.HALF_UP).subtract(stockNow);
        if (i.getStatus() != ItemResponse.Status.OK && lastQuantity != null) need = need.max(lastQuantity);
        if (i.getLowStockLevel() != null) need = need.max(i.getLowStockLevel().multiply(BigDecimal.valueOf(2)).subtract(stockNow));
        return need.setScale(0, RoundingMode.CEILING).max(BigDecimal.ONE);
    }

    private Product resolve(UUID ownerId, PurchaseRequest.Line l) {
        if (l.getItemId() != null) {
            return products.findById(l.getItemId())
                    .filter(p -> ownerId.equals(p.getOwnerId()) && Boolean.TRUE.equals(p.getIsActive()))
                    .orElseThrow(() -> new ResourceNotFoundException("Item could not be found"));
        }
        ItemRequest n = l.getNewItem();
        if (n == null) throw invalid("Pick an item or type a new one");
        ItemRequest create = new ItemRequest();
        create.setName(n.getName());
        create.setPrice(n.getPrice());
        create.setUnit(n.getUnit());
        create.setLowStockLevel(n.getLowStockLevel());
        create.setTrackStock(true);
        UUID id = items.create(ownerId, create).getId();
        return products.findById(id).orElseThrow();
    }

    private List<PurchaseResponse> build(UUID ownerId, List<StockPurchase> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, List<StockPurchaseItem>> byPurchase = lines.findByPurchaseIdIn(list.stream().map(StockPurchase::getId).toList())
                .stream().collect(Collectors.groupingBy(StockPurchaseItem::getPurchaseId));
        Set<UUID> productIds = byPurchase.values().stream().flatMap(List::stream).map(StockPurchaseItem::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productById = products.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Set<UUID> deleted = purchases.findByOwnerIdOrderByBoughtOnDescCreatedAtDesc(ownerId).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsActive())).map(StockPurchase::getId).collect(Collectors.toSet());
        Map<UUID, Map<UUID, BigDecimal>> leftByProduct = new HashMap<>();
        if (!productIds.isEmpty()) {
            Map<UUID, List<StockMovement>> moves = movements.findByOwnerIdAndProductIdInOrderByCreatedAtAsc(ownerId, productIds)
                    .stream().collect(Collectors.groupingBy(StockMovement::getProductId));
            moves.forEach((pid, ms) -> leftByProduct.put(pid, PurchaseMath.leftByPurchase(ms, deleted)));
        }

        List<PurchaseResponse> out = new ArrayList<>();
        for (StockPurchase p : list) {
            List<PurchaseResponse.Line> rows = new ArrayList<>();
            BigDecimal costLeft = BigDecimal.ZERO;
            BigDecimal soldValue = BigDecimal.ZERO;
            boolean finished = true;
            boolean untouched = true;
            for (StockPurchaseItem line : byPurchase.getOrDefault(p.getId(), List.of())) {
                Product prod = productById.get(line.getProductId());
                BigDecimal left = leftByProduct.getOrDefault(line.getProductId(), Map.of())
                        .getOrDefault(p.getId(), BigDecimal.ZERO)
                        .max(BigDecimal.ZERO).min(line.getQuantity());
                BigDecimal price = prod == null ? BigDecimal.ZERO : prod.getPrice();
                costLeft = costLeft.add(left.multiply(line.getUnitCost()));
                soldValue = soldValue.add(line.getQuantity().subtract(left).multiply(price));
                if (left.signum() > 0) finished = false;
                if (left.compareTo(line.getQuantity()) < 0) untouched = false;
                rows.add(new PurchaseResponse.Line(line.getProductId(), prod == null ? "Deleted item" : prod.getName(),
                        prod == null || prod.getUnit() == null ? StockUnit.PCS : prod.getUnit(),
                        line.getQuantity().stripTrailingZeros(), left.stripTrailingZeros(),
                        line.getUnitCost().setScale(2, RoundingMode.HALF_UP), price));
            }
            BigDecimal paid = p.getTotalPaid();
            costLeft = costLeft.setScale(2, RoundingMode.HALF_UP);
            soldValue = soldValue.setScale(2, RoundingMode.HALF_UP);
            int leftPercent = paid.signum() == 0 ? (finished ? 0 : 100)
                    : costLeft.multiply(BigDecimal.valueOf(100)).divide(paid, 0, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)).intValue();
            BigDecimal profit = soldValue.subtract(paid.subtract(costLeft)).setScale(2, RoundingMode.HALF_UP);
            out.add(new PurchaseResponse(p.getId(), p.getSupplier(), p.getBoughtOn(), paid, p.getNote(), rows,
                    leftPercent, costLeft, soldValue, soldValue.compareTo(paid) >= 0, profit, finished, untouched, p.getCreatedAt()));
        }
        return out;
    }

    private StockPurchase find(UUID ownerId, UUID id) {
        return purchases.findById(id)
                .filter(p -> ownerId.equals(p.getOwnerId()) && Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Purchase could not be found"));
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCodes.INVALID, message);
    }

    private static void requireSelf(UUID ownerId) {
        if (!ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only see your own purchases");
        }
    }
}
