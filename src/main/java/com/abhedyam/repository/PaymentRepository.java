package com.abhedyam.repository;

import com.abhedyam.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOwnerId(UUID ownerId);
    Page<Payment> findByOwnerId(UUID ownerId, Pageable pageable);
    List<Payment> findByCustomerId(UUID customerId);
    List<Payment> findByCustomerIdAndOwnerId(UUID customerId, UUID ownerId);
    
    @Query("SELECT DISTINCT p FROM Payment p " +
           "LEFT JOIN Customer c ON c.id = p.customerId " +
           "LEFT JOIN SaleItem si ON si.id = p.saleItemId " +
           "LEFT JOIN Product pr ON pr.id = si.productId " +
           "WHERE p.ownerId = :ownerId " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (pr.name IS NOT NULL AND LOWER(pr.name) LIKE LOWER(CONCAT('%', :searchText, '%'))) OR " +
           "     (:isNumeric = true AND p.amount = :amount))")
    List<Payment> filterPayments(@Param("ownerId") UUID ownerId,
                                 @Param("searchText") String searchText,
                                 @Param("isNumeric") boolean isNumeric,
                                 @Param("amount") BigDecimal amount);

    @Query("SELECT p FROM Payment p " +
           "LEFT JOIN Customer c ON c.id = p.customerId " +
           "LEFT JOIN SaleItem si ON si.id = p.saleItemId " +
           "LEFT JOIN Product pr ON pr.id = si.productId " +
           "WHERE p.ownerId = :ownerId " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (pr.name IS NOT NULL AND LOWER(pr.name) LIKE LOWER(CONCAT('%', :searchText, '%'))) OR " +
           "     (:isNumeric = true AND p.amount = :amount))")
    Page<Payment> searchPayments(@Param("ownerId") UUID ownerId,
                                 @Param("searchText") String searchText,
                                 @Param("isNumeric") boolean isNumeric,
                                 @Param("amount") BigDecimal amount,
                                 Pageable pageable);
    
    @Query("SELECT p FROM Payment p WHERE p.customerId IN :customerIds AND p.ownerId = :ownerId")
    List<Payment> findByCustomerIdInAndOwnerId(@Param("customerIds") List<UUID> customerIds,
                                                @Param("ownerId") UUID ownerId);
    
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.ownerId = :ownerId " +
           "AND p.status = 'SUCCESS' " +
           "AND (:startDate IS NULL OR p.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR p.createdAt <= :endDate)")
    long countSuccessfulPaymentsByOwnerIdAndDateRange(@Param("ownerId") UUID ownerId,
                                                       @Param("startDate") java.time.Instant startDate,
                                                       @Param("endDate") java.time.Instant endDate);

    @Query("SELECT p.customerId, SUM(p.amount) FROM Payment p " +
           "WHERE p.ownerId = :ownerId AND p.status = 'SUCCESS' GROUP BY p.customerId")
    List<Object[]> sumSuccessfulByCustomer(@Param("ownerId") UUID ownerId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.ownerId = :ownerId AND p.status = 'SUCCESS' " +
           "AND p.timestamp >= :from AND p.timestamp < :to")
    BigDecimal sumSuccessfulBetween(@Param("ownerId") UUID ownerId,
                                    @Param("from") java.time.Instant from,
                                    @Param("to") java.time.Instant to);
}

