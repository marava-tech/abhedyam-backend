package com.abhedyam.repository;

import com.abhedyam.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    
    @Query("SELECT d FROM Document d WHERE d.ownerId = :ownerId AND d.isActive = true ORDER BY d.orderIndex ASC")
    List<Document> findActiveDocumentsByOwnerId(@Param("ownerId") UUID ownerId);
    
    @Query("SELECT d FROM Document d WHERE d.ownerId = :ownerId AND d.isActive = true AND d.visibleToCustomers = true ORDER BY d.orderIndex ASC")
    List<Document> findVisibleDocumentsByOwnerId(@Param("ownerId") UUID ownerId);
}

