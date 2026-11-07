package com.abhedyam.repository;

import com.abhedyam.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    List<Inventory> findByOwnerId(UUID ownerId);
    Optional<Inventory> findByOwnerIdAndProductId(UUID ownerId, UUID productId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM Inventory i WHERE i.ownerId = :ownerId AND i.productId = :productId")
    Optional<Inventory> lockByOwnerIdAndProductId(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
                                                  @org.springframework.data.repository.query.Param("productId") UUID productId);
    
    @org.springframework.data.jpa.repository.Query("SELECT i FROM Inventory i WHERE i.ownerId = :ownerId AND i.productId IN :productIds")
    List<Inventory> findByOwnerIdAndProductIdIn(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
                                                 @org.springframework.data.repository.query.Param("productIds") List<UUID> productIds);
}

