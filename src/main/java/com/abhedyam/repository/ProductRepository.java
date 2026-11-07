package com.abhedyam.repository;

import com.abhedyam.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
       List<Product> findByOwnerId(UUID ownerId);

       Optional<Product> findByOwnerIdAndCode(UUID ownerId, String code);

       @Query("SELECT p FROM Product p WHERE p.ownerId = :ownerId " +
                     "AND (:isActive IS NULL OR p.isActive = :isActive) " +
                     "AND (:searchTerm IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
                     "OR LOWER(p.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
       Page<Product> searchProducts(@Param("ownerId") UUID ownerId,
                     @Param("searchTerm") String searchTerm,
                     @Param("isActive") Boolean isActive,
                     Pageable pageable);

       @Query("SELECT p FROM Product p WHERE p.id IN :productIds")
       List<Product> findByIdIn(@Param("productIds") List<UUID> productIds);

       @Query("SELECT p FROM Product p WHERE p.ownerId = :ownerId " +
                     "AND LOWER(TRIM(p.name)) = LOWER(TRIM(:name))")
       List<Product> findByOwnerIdAndName(@Param("ownerId") UUID ownerId,
                     @Param("name") String name);
}
