package com.abhedyam.repository;

import com.abhedyam.model.SaleItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {
    List<SaleItem> findByOwnerId(UUID ownerId);
    List<SaleItem> findByOwnerIdAndCreatedAtAfter(UUID ownerId, Instant after);
    List<SaleItem> findByCustomerId(UUID customerId);
    List<SaleItem> findByCustomerIdAndOwnerId(UUID customerId, UUID ownerId);
    List<SaleItem> findByTransactionId(String transactionId);
    
    @Query("SELECT si FROM SaleItem si WHERE si.id IN :saleItemIds")
    List<SaleItem> findByIdIn(@Param("saleItemIds") List<UUID> saleItemIds);
    
    @Query("SELECT s FROM SaleItem s WHERE s.ownerId = :ownerId " +
           "AND (:customerId IS NULL OR s.customerId = :customerId) " +
           "AND (:transactionId IS NULL OR s.transactionId = :transactionId) " +
           "AND (:startDate IS NULL OR s.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR s.createdAt <= :endDate)")
    Page<SaleItem> searchSales(@Param("ownerId") UUID ownerId,
                               @Param("customerId") UUID customerId,
                               @Param("transactionId") String transactionId,
                               @Param("startDate") Instant startDate,
                               @Param("endDate") Instant endDate,
                               Pageable pageable);
    
    @Query("SELECT s FROM SaleItem s WHERE s.customerId IN :customerIds AND s.ownerId = :ownerId")
    List<SaleItem> findByCustomerIdAndOwnerIdIn(@Param("customerIds") List<UUID> customerIds,
                                                  @Param("ownerId") UUID ownerId);
    
    @Query("SELECT COUNT(DISTINCT s.transactionId) FROM SaleItem s WHERE s.ownerId = :ownerId " +
           "AND (:startDate IS NULL OR s.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR s.createdAt <= :endDate)")
    long countDistinctSalesByOwnerIdAndDateRange(@Param("ownerId") UUID ownerId,
                                                  @Param("startDate") java.time.Instant startDate,
                                                  @Param("endDate") java.time.Instant endDate);

    @Query("SELECT s.customerId, SUM(s.price * s.quantity) FROM SaleItem s " +
           "WHERE s.ownerId = :ownerId GROUP BY s.customerId")
    List<Object[]> sumTotalsByCustomer(@Param("ownerId") UUID ownerId);

    List<SaleItem> findByCustomerIdAndOwnerIdOrderByCreatedAtAsc(UUID customerId, UUID ownerId);

    @Query("SELECT s.productId, s.quantity, s.createdAt FROM SaleItem s WHERE s.ownerId = :ownerId AND s.isActive = true")
    List<Object[]> saleLinesByOwner(@Param("ownerId") UUID ownerId);
}
