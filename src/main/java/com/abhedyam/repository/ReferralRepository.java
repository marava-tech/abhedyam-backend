package com.abhedyam.repository;

import com.abhedyam.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    List<Referral> findByReferrerOwnerIdOrderByCreatedAtDesc(UUID referrerOwnerId);

    Optional<Referral> findByRefereeOwnerId(UUID refereeOwnerId);
}
