package com.abhedyam.service.interfaces;

import com.abhedyam.dto.StockAdjustmentRequest;
import com.abhedyam.dto.StockUpdateRequest;
import com.abhedyam.model.Product;
import com.abhedyam.model.enums.StockMovementType;
import com.abhedyam.model.enums.StockRemovalReason;

import java.math.BigDecimal;
import java.util.UUID;

public interface IStockService {
    BigDecimal apply(UUID ownerId, Product product, StockMovementType type, BigDecimal change,
                     StockRemovalReason reason, String note, String saleTransactionId, UUID purchaseId);

    default BigDecimal apply(UUID ownerId, Product product, StockMovementType type, BigDecimal change,
                             StockRemovalReason reason, String note, String saleTransactionId) {
        return apply(ownerId, product, type, change, reason, note, saleTransactionId, null);
    }

    BigDecimal count(UUID ownerId, Product product, BigDecimal counted, String note);

    void saleOut(UUID ownerId, UUID productId, BigDecimal quantity, String saleTransactionId);

    void saleCancelled(UUID ownerId, UUID productId, BigDecimal quantity, String saleTransactionId);

    BigDecimal currentStock(UUID ownerId, UUID productId);

    void recordPurchaseIn(UUID productId, BigDecimal quantity, String note);

    void recordSaleOut(UUID productId, BigDecimal quantity, UUID saleItemId, String note);

    void recordManualAdjustment(StockAdjustmentRequest request);

    void updateStock(StockUpdateRequest request);

    BigDecimal getCurrentStock(UUID productId);
}
