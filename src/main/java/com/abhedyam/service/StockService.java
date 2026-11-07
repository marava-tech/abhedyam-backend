package com.abhedyam.service;

import com.abhedyam.model.Inventory;
import com.abhedyam.model.Product;
import com.abhedyam.model.StockMovement;
import com.abhedyam.model.enums.StockMovementType;
import com.abhedyam.model.enums.StockRemovalReason;
import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.StockMovementRepository;
import com.abhedyam.service.interfaces.IStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Every stock change is a {@link StockMovement} plus the running total in {@code inventories}, written together with
 * the inventory row locked. The sale paths ({@link #saleOut}, {@link #saleCancelled}) join the sale's transaction
 * (a rolled-back sale undoes its stock) and catch their own errors, so no exception crosses the proxy and marks
 * the sale rollback-only. A database error inside a repository call can still do that; nothing short of a
 * separate transaction avoids it, and a separate one would keep stock for sales that failed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService implements IStockService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryService inventoryService;
    private final com.abhedyam.service.interfaces.IAuditService auditService;

    @Override
    @Transactional
    public BigDecimal apply(UUID ownerId, Product product, StockMovementType type, BigDecimal change,
                            StockRemovalReason reason, String note, String saleTransactionId, UUID purchaseId) {
        Inventory inventory = inventoryRepository.lockByOwnerIdAndProductId(ownerId, product.getId())
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setProductId(product.getId());
                    inv.setOwnerId(ownerId);
                    inv.setStock(BigDecimal.ZERO);
                    return inv;
                });
        BigDecimal current = inventory.getStock() == null ? BigDecimal.ZERO : inventory.getStock();
        BigDecimal after = current.add(change).setScale(3, RoundingMode.HALF_UP);

        StockMovement m = new StockMovement();
        m.setOwnerId(ownerId);
        m.setProductId(product.getId());
        m.setType(type);
        m.setQuantity(change.setScale(3, RoundingMode.HALF_UP));
        m.setStockAfter(after);
        m.setReason(type == StockMovementType.REMOVED ? (reason == null ? StockRemovalReason.OTHER : reason) : null);
        m.setNote(note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(200, note.trim().length())));
        m.setSaleTransactionId(saleTransactionId);
        m.setPurchaseId(purchaseId);
        movementRepository.save(m);

        inventory.setStock(after);
        inventoryRepository.save(inventory);
        inventoryService.invalidateOwnerCaches(ownerId);
        return after;
    }

    @Override
    @Transactional
    public BigDecimal count(UUID ownerId, Product product, BigDecimal counted, String note) {
        boolean first = !movementRepository.existsByOwnerIdAndProductId(ownerId, product.getId());
        startTracking(product);
        BigDecimal current = currentStock(ownerId, product.getId());
        BigDecimal change = counted.subtract(current);
        if (change.signum() == 0 && !first) return current;
        return apply(ownerId, product, first ? StockMovementType.OPENING : StockMovementType.COUNT, change, null, note, null);
    }

    @Override
    @Transactional
    public void saleOut(UUID ownerId, UUID productId, BigDecimal quantity, String saleTransactionId) {
        try {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null || !ownerId.equals(product.getOwnerId()) || !product.tracksStock()) return;
            apply(ownerId, product, StockMovementType.SALE, quantity.negate(), null, null, saleTransactionId);
        } catch (RuntimeException e) {
            log.error("Stock not reduced for product {} in sale {}: {}", productId, saleTransactionId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void saleCancelled(UUID ownerId, UUID productId, BigDecimal quantity, String saleTransactionId) {
        try {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null || !ownerId.equals(product.getOwnerId()) || !product.tracksStock()) return;
            apply(ownerId, product, StockMovementType.SALE_CANCELLED, quantity, null, null, saleTransactionId);
        } catch (RuntimeException e) {
            log.error("Stock not restored for product {} in cancelled sale {}: {}", productId, saleTransactionId, e.getMessage());
        }
    }

    @Override
    public BigDecimal currentStock(UUID ownerId, UUID productId) {
        return inventoryRepository.findByOwnerIdAndProductId(ownerId, productId)
                .map(Inventory::getStock)
                .orElse(BigDecimal.ZERO);
    }

    private void startTracking(Product product) {
        if (!product.tracksStock()) {
            product.setTrackStock(true);
            productRepository.save(product);
        }
    }
}
