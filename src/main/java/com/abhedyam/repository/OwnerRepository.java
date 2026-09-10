package com.abhedyam.repository;

import com.abhedyam.model.Owner;
import com.abhedyam.model.enums.SubscriptionStatus;
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
public interface OwnerRepository extends JpaRepository<Owner, UUID> {
    @Query("SELECT o FROM Owner o WHERE " +
           "(:username IS NULL OR LOWER(o.name) LIKE LOWER(CONCAT('%', :username, '%'))) " +
           "AND (:email IS NULL OR LOWER(o.email) LIKE LOWER(CONCAT('%', :email, '%')))")
    Page<Owner> searchOwners(@Param("username") String username,
                              @Param("email") String email,
                              Pageable pageable);

    List<Owner> findBySubscriptionStatusAndValidTillBefore(SubscriptionStatus subscriptionStatus, Instant validTill);

    java.util.Optional<Owner> findByPublicSlug(String publicSlug);

    List<Owner> findByPublicListingEnabledTrueAndIsActiveTrue();
}

