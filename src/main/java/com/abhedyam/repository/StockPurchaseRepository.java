package com.abhedyam.repository;

import com.abhedyam.model.StockPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockPurchaseRepository extends JpaRepository<StockPurchase, UUID> {
    List<StockPurchase> findByOwnerIdOrderByBoughtOnDescCreatedAtDesc(UUID ownerId);
}
