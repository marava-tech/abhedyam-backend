package com.abhedyam.repository;

import com.abhedyam.model.Owner;
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

    long countByFirstPaidAtIsNotNull();

    /** Trial shops that have not earned the second free month yet. */
    java.util.List<Owner> findByFirstPaidAtIsNullAndPlayProductIdIsNullAndTrialBonusAtIsNullAndAccessUntilAfter(java.time.Instant now);

    List<Owner> findByPlayProductIdIsNullAndIsActiveTrueAndCreatedAtAfter(Instant after);

    long countByPartnerId(String partnerId);

    List<Owner> findByPlayProductIdIsNullAndAccessUntilBetween(Instant from, Instant to);

    java.util.Optional<Owner> findByPublicSlug(String publicSlug);

    java.util.Optional<Owner> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    boolean existsByIdAndIsActiveTrue(UUID id);

    /** Owners disabled (account deleted) before the given time. Only disableAccount sets these two fields on an owner. */
    List<Owner> findByIsActiveFalseAndDeletedAtBefore(Instant before);
}

