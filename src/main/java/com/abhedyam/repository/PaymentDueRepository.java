package com.abhedyam.repository;

import com.abhedyam.model.PaymentDue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentDueRepository extends JpaRepository<PaymentDue, UUID> {
    List<PaymentDue> findByOwnerIdAndCustomerIdAndIsActiveTrue(UUID ownerId, UUID customerId);

    List<PaymentDue> findByOwnerIdAndIsActiveTrue(UUID ownerId);
}
