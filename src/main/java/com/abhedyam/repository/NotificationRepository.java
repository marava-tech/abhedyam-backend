package com.abhedyam.repository;

import com.abhedyam.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByOwnerId(UUID ownerId);
    List<Notification> findByUserId(UUID userId);
    java.util.Optional<Notification> findByOwnerIdAndDedupeKey(UUID ownerId, String dedupeKey);
    List<Notification> findByDeliveryStatusAndPushAfterLessThanEqual(com.abhedyam.model.enums.DeliveryStatus status, java.time.Instant at);
    
    @Query("SELECT n FROM Notification n WHERE (n.ownerId = :userId OR n.userId = :userId) " +
           "AND (n.isActive = true OR n.isActive IS NULL) " +
           "ORDER BY n.timestamp DESC")
    List<Notification> findByOwnerIdOrUserId(@Param("userId") UUID userId);
    
    @Query("SELECT n FROM Notification n WHERE (n.ownerId = :userId OR n.userId = :userId) " +
           "AND (n.isActive = true OR n.isActive IS NULL) " +
           "AND (n.isRead = false OR n.isRead IS NULL) " +
           "ORDER BY n.timestamp DESC")
    List<Notification> findUnreadByOwnerIdOrUserId(@Param("userId") UUID userId);
}

