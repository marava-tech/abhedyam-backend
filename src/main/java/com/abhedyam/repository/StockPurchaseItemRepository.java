package com.abhedyam.repository;

import com.abhedyam.model.StockPurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockPurchaseItemRepository extends JpaRepository<StockPurchaseItem, UUID> {
    List<StockPurchaseItem> findByPurchaseIdIn(Collection<UUID> purchaseIds);
}
