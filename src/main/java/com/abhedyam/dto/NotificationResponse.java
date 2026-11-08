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
    /** Always 0. App 1.11.1 parses it as required; drop once 1.12+ is what owners run. */
    private Integer retryCount;
    private Instant createdAt;
    private String category;
    /** Push title ("Prava pays tomorrow"); null on rows from before the dispatcher. */
    private String title;
    
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
            0,
            notification.getCreatedAt(),
            notification.getCategory() == null ? null : notification.getCategory().name(),
            notification.getTitle()
        );
    }
}

