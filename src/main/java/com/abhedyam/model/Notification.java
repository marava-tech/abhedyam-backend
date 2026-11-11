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
    @Column(length = 40, columnDefinition = "VARCHAR(40)")
    private NotificationCategory category;

    /** Push title; the in-app message is {@link #message}. */
    @Column
    private String title;

    /** The title and message in the other app languages, JSON {"hi": [...], "te": ["title", "body"]} (title/message are English). */
    @Column(columnDefinition = "TEXT")
    private String texts;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Title and message in every app language: English into title/message, the others into {@link #texts}. */
    public void setLocalized(java.util.Map<com.abhedyam.model.enums.AppLanguage, String[]> all) {
        java.util.Map<String, java.util.List<String>> other = new java.util.LinkedHashMap<>();
        all.forEach((lang, t) -> {
            if (lang == com.abhedyam.model.enums.AppLanguage.EN) {
                title = t[0];
                message = t[1];
            } else {
                other.put(lang.code(), java.util.Arrays.asList(t));
            }
        });
        try {
            texts = other.isEmpty() ? null : JSON.writeValueAsString(other);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            texts = null; // English still goes out
        }
    }

    /** {title, message} in a language code ("te"); English when the row has no text in it (older rows). */
    public String[] textIn(String lang) {
        if (texts != null && lang != null) {
            try {
                java.util.List<String> t = JSON.readValue(texts, new com.fasterxml.jackson.core.type.TypeReference<
                        java.util.Map<String, java.util.List<String>>>() {}).get(lang);
                if (t != null && t.size() == 2) return new String[] {t.get(0), t.get(1)};
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // fall through to English
            }
        }
        return new String[] {title, message};
    }

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

