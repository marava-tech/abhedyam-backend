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

    boolean existsByOwnerIdAndProductId(UUID ownerId, UUID productId);
}
