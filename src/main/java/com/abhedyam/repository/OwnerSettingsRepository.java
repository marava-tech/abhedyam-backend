package com.abhedyam.repository;

import com.abhedyam.model.OwnerSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OwnerSettingsRepository extends JpaRepository<OwnerSettings, UUID> {
    Optional<OwnerSettings> findByOwnerId(UUID ownerId);

    java.util.List<OwnerSettings> findByDeletionRequestedAtNotNull();
}

