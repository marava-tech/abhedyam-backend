package com.abhedyam.repository;

import com.abhedyam.model.FounderCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FounderCallRepository extends JpaRepository<FounderCall, UUID> {
    List<FounderCall> findByOwnerIdInOrderByCreatedAtDesc(Collection<UUID> ownerIds);
}
