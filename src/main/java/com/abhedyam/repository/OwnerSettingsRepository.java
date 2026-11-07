package com.abhedyam.repository;

import com.abhedyam.model.OwnerSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OwnerSettingsRepository extends JpaRepository<OwnerSettings, UUID> {
    Optional<OwnerSettings> findByOwnerId(UUID ownerId);

    /** Race-safe create: parallel first requests both insert, the loser is ignored. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value =
        "INSERT IGNORE INTO owner_settings (id, owner_id, is_dark_mode_enabled, is_active, created_at, updated_at) "
        + "VALUES (:id, :id, 0, 1, NOW(), NOW())")
    void insertDefaultsIfAbsent(@org.springframework.data.repository.query.Param("id") String id);

    java.util.List<OwnerSettings> findByDeletionRequestedAtNotNull();
}

