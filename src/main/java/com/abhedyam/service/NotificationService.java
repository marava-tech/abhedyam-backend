package com.abhedyam.service;

import com.abhedyam.dto.NotificationMarkReadRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Notification;
import com.abhedyam.repository.NotificationRepository;
import com.abhedyam.service.interfaces.INotificationService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService implements INotificationService {
    
    private final NotificationRepository notificationRepository;
    
    @Override
    public Notification create(Notification notification) {
        if (notification.getOwnerId() == null) {
            throw new BusinessException("INVALID_NOTIFICATION", "Notification ownerId cannot be null");
        }
        if (notification.getUserId() == null) {
            throw new BusinessException("INVALID_NOTIFICATION", "Notification userId cannot be null");
        }
        if (notification.getRetryCount() == null) {
            notification.setRetryCount(0);
        }
        if (notification.getIsRead() == null) {
            notification.setIsRead(false);
        }
        return notificationRepository.save(notification);
    }
    
    @Override
    public Notification getById(UUID id) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification could not be found"));
        
        if (!notification.getOwnerId().equals(currentUserId) && !notification.getUserId().equals(currentUserId)) {
            throw new BusinessException("UNAUTHORIZED", "You don't have permission to access this notification");
        }
        
        return notification;
    }
    
    @Override
    public List<Notification> getMyNotifications(Boolean unreadOnly) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        
        if (unreadOnly != null && unreadOnly) {
            return notificationRepository.findUnreadByOwnerIdOrUserId(currentUserId);
        }
        
        return notificationRepository.findByOwnerIdOrUserId(currentUserId);
    }

    @Override
    public List<Notification> getNotificationsForUser(UUID userId, Boolean unreadOnly) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new BusinessException("UNAUTHORIZED", "You can only access your own notifications");
        }
        return getMyNotifications(unreadOnly);
    }
    
    @Override
    @Transactional
    public Notification markAsRead(UUID id) {
        Notification notification = getById(id);
        notification.setIsRead(true);
        notification.setReadAt(Instant.now());
        return notificationRepository.save(notification);
    }
    
    @Override
    @Transactional
    public List<Notification> markMultipleAsRead(NotificationMarkReadRequest request) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        Instant now = Instant.now();
        
        if (request.getMarkAllAsRead() != null && request.getMarkAllAsRead()) {
            List<Notification> notifications = notificationRepository.findUnreadByOwnerIdOrUserId(currentUserId);
            
            notifications.forEach(n -> {
                n.setIsRead(true);
                n.setReadAt(now);
            });
            
            return notificationRepository.saveAll(notifications);
        }
        
        if (request.getNotificationIds() != null && !request.getNotificationIds().isEmpty()) {
            List<Notification> notifications = notificationRepository.findAllById(request.getNotificationIds()).stream()
                .filter(n -> n.getOwnerId().equals(currentUserId) || n.getUserId().equals(currentUserId))
                .toList();
            
            notifications.forEach(n -> {
                n.setIsRead(true);
                n.setReadAt(now);
            });
            
            return notificationRepository.saveAll(notifications);
        }
        
        return List.of();
    }
}

