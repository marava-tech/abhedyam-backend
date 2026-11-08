package com.abhedyam.model;

import com.abhedyam.model.enums.DeliveryStatus;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(columnNames = {"ownerId", "dedupeKey"}))
@Getter
@Setter
public class Notification extends BaseEntity {
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;
    
    @Column(nullable = false)
    private String message;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column(nullable = false)
    private Boolean isRead = false;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID relatedEntityId;
    
    @Column
    private String relatedEntityType;
    
    @Column
    private Instant readAt;

    /** Why this was sent (null on rows created before the dispatcher existed). */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private NotificationCategory category;

    /** Push title; the in-app message is {@link #message}. */
    @Column
    private String title;

    /** Same key twice for one owner = same event; the second is ignored. Nullable. */
    @Column
    private String dedupeKey;

    /** Earliest time the push may go out (quiet hours). */
    @Column
    private Instant pushAfter;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeliveryStatus deliveryStatus;
}

