package com.abhedyam.service.interfaces;

import com.abhedyam.dto.NotificationMarkReadRequest;
import com.abhedyam.model.Notification;

import java.util.List;
import java.util.UUID;

public interface INotificationService {
    Notification create(Notification notification);
    Notification getById(UUID id);
    List<Notification> getMyNotifications(Boolean unreadOnly);
    List<Notification> getNotificationsForUser(UUID userId, Boolean unreadOnly);
    Notification markAsRead(UUID id);
    List<Notification> markMultipleAsRead(NotificationMarkReadRequest request);
}

