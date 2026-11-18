package com.abhedyam.repository;

import com.abhedyam.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    Page<StockMovement> findByOwnerIdAndProductIdOrderByCreatedAtDesc(UUID ownerId, UUID productId, Pageable pageable);

    /** (productId, quantity, createdAt) of every stock-up (OPENING / RECEIVED): the newest one sets the automatic running-out level. */
    @org.springframework.data.jpa.repository.Query("select m.productId, m.quantity, m.createdAt from StockMovement m where m.ownerId = :ownerId and m.type in (com.abhedyam.model.enums.StockMovementType.OPENING, com.abhedyam.model.enums.StockMovementType.RECEIVED)")
    java.util.List<Object[]> stockUps(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId);

    boolean existsByOwnerIdAndProductId(UUID ownerId, UUID productId);

    java.util.List<StockMovement> findByOwnerIdAndProductIdInOrderByCreatedAtAsc(UUID ownerId, java.util.Collection<UUID> productIds);
}
