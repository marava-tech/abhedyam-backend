package com.abhedyam.repository;

import com.abhedyam.model.OwnerSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerSessionRepository extends JpaRepository<OwnerSession, UUID> {
    boolean existsByIdAndRevokedAtIsNotNull(UUID id);

    List<OwnerSession> findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(UUID ownerId);

    Optional<OwnerSession> findFirstByOwnerIdAndDeviceIdAndRevokedAtIsNull(UUID ownerId, String deviceId);

    List<OwnerSession> findByMemberIdAndRevokedAtIsNull(UUID memberId);

    java.util.Optional<OwnerSession> findFirstByOwnerIdAndDeviceIdAndLabelIsNotNullOrderByCreatedAtDesc(UUID ownerId, String deviceId);
}
