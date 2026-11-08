package com.abhedyam.repository;

import com.abhedyam.model.Referral;
import com.abhedyam.model.enums.ReferralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    List<Referral> findByReferrerOwnerIdOrderByCreatedAtDesc(UUID referrerOwnerId);

    Optional<Referral> findByRefereeOwnerId(UUID refereeOwnerId);

    boolean existsByRefereeOwnerId(UUID refereeOwnerId);

    List<Referral> findByStatus(ReferralStatus status);

    long countByReferrerOwnerIdAndStatus(UUID referrerOwnerId, ReferralStatus status);

    long countByReferrerOwnerId(UUID referrerOwnerId);
}
