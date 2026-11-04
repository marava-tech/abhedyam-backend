package com.abhedyam.model;

import com.abhedyam.model.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One row per owner, created on first change; until then the defaults below apply. */
@Entity
@Table(name = "notification_settings", uniqueConstraints = @UniqueConstraint(columnNames = {"ownerId"}))
@Getter
@Setter
public class NotificationSettings extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @Column(nullable = false)
    private LocalTime quietStart = LocalTime.of(21, 0);

    @Column(nullable = false)
    private LocalTime quietEnd = LocalTime.of(8, 0);

    @Column(nullable = false)
    private LocalTime digestTime = LocalTime.of(8, 0);

    /** IANA zone, e.g. Asia/Kolkata. */
    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    /** Categories the owner turned off for push. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_muted_categories", joinColumns = @JoinColumn(name = "settings_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40)
    private Set<NotificationCategory> mutedCategories = new HashSet<>();
}
