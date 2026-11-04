package com.abhedyam.dto;

import com.abhedyam.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private UUID ownerId;
    private UUID userId;
    private String type;
    private String message;
    private Instant timestamp;
    private Boolean isRead;
    private Instant readAt;
    private UUID relatedEntityId;
    private String relatedEntityType;
    private Integer retryCount;
    private Instant createdAt;
    private String category;
    
    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getOwnerId(),
            notification.getUserId(),
            notification.getType().name(),
            notification.getMessage(),
            notification.getTimestamp(),
            notification.getIsRead(),
            notification.getReadAt(),
            notification.getRelatedEntityId(),
            notification.getRelatedEntityType(),
            notification.getRetryCount(),
            notification.getCreatedAt(),
            notification.getCategory() == null ? null : notification.getCategory().name()
        );
    }
}

