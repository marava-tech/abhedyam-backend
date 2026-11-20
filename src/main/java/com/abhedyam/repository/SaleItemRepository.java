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

    java.util.Optional<SaleItem> findFirstByOwnerIdAndClientKey(UUID ownerId, String clientKey);

    /** Sales report totals for [from, to): sales amount, bills, customers, units sold. Always one row. */
    @Query("SELECT COALESCE(SUM(s.price * s.quantity), 0), COUNT(DISTINCT s.transactionId), " +
           "COUNT(DISTINCT s.customerId), COALESCE(SUM(s.quantity), 0) FROM SaleItem s " +
           "WHERE s.ownerId = :ownerId AND s.isActive = true AND COALESCE(s.oldBalance, false) = false " +
           "AND s.createdAt >= :from AND s.createdAt < :to")
    List<Object[]> periodTotals(@Param("ownerId") UUID ownerId, @Param("from") Instant from, @Param("to") Instant to);

    /** Report rows: when, line total, bill, customer. Grouped by the owner's day in ReportMath. */
    @Query("SELECT s.createdAt, s.price * s.quantity, s.transactionId, s.customerId FROM SaleItem s " +
           "WHERE s.ownerId = :ownerId AND s.isActive = true AND s.createdAt >= :from AND s.createdAt < :to")
    List<Object[]> reportRows(@Param("ownerId") UUID ownerId, @Param("from") Instant from, @Param("to") Instant to);

    /** A sale entered later (saved offline): dated when it was made, not when it reached the server. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("UPDATE SaleItem s SET s.createdAt = :at WHERE s.transactionId = :transactionId")
    int backdate(@Param("transactionId") String transactionId, @Param("at") Instant at);
    
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

    /** customerId, created-at of that customer's latest sale. */
    @Query("SELECT s.customerId, MAX(s.createdAt) FROM SaleItem s WHERE s.ownerId = :ownerId " +
           "AND COALESCE(s.oldBalance, false) = false GROUP BY s.customerId")
    List<Object[]> lastSaleByCustomer(@Param("ownerId") UUID ownerId);

    /** customerId, created-at of that customer's oldest sale not yet fully paid. */
    @Query("SELECT s.customerId, MIN(s.createdAt) FROM SaleItem s " +
           "WHERE s.ownerId = :ownerId AND s.status <> com.abhedyam.model.enums.SaleItemStatus.FULLY_PAID " +
           "GROUP BY s.customerId")
    List<Object[]> oldestUnpaidSaleByCustomer(@Param("ownerId") UUID ownerId);

    List<SaleItem> findByCustomerIdAndOwnerIdOrderByCreatedAtAsc(UUID customerId, UUID ownerId);
}
