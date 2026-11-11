package com.abhedyam.repository;

import com.abhedyam.model.Audit;
import com.abhedyam.model.enums.AuditAction;
import com.abhedyam.model.enums.AuditType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<Audit, UUID> {
    List<Audit> findByOwnerId(UUID ownerId);
    
    @Query("SELECT a FROM Audit a WHERE a.ownerId = :ownerId " +
           "AND a.type IN :types " +
           "AND a.action IN :actions " +
           "ORDER BY a.timestamp DESC")
    Page<Audit> findRecentActivities(@Param("ownerId") UUID ownerId,
                                     @Param("types") List<AuditType> types,
                                     @Param("actions") List<AuditAction> actions,
                                     Pageable pageable);

    /** Drops the activity rows of cancelled payments, so Home's Recent activity no longer shows them. */
    @Modifying
    @Query("DELETE FROM Audit a WHERE a.ownerId = :ownerId AND a.type = com.abhedyam.model.enums.AuditType.PAYMENT AND a.entityId IN :paymentIds")
    int deletePaymentActivities(@Param("ownerId") UUID ownerId, @Param("paymentIds") List<UUID> paymentIds);
}
