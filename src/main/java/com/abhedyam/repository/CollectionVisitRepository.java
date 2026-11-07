package com.abhedyam.repository;

import com.abhedyam.model.CollectionVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CollectionVisitRepository extends JpaRepository<CollectionVisit, UUID> {
    List<CollectionVisit> findByOwnerIdAndIsActiveTrueAndDoneAtIsNull(UUID ownerId);
}
