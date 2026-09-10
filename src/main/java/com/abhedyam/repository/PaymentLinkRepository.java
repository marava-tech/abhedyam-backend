package com.abhedyam.repository;

import com.abhedyam.model.PaymentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {
    Optional<PaymentLink> findByTokenAndIsActiveTrue(String token);
}
