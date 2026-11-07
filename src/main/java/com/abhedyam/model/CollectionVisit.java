package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** "Visit Hanamkonda on 12 Nov" in Upcoming collections. Who owes there and how much is computed when listed. */
@Entity
@Table(name = "collection_visits", indexes = @Index(name = "idx_collection_visits_owner", columnList = "ownerId"))
@Getter
@Setter
public class CollectionVisit extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String village;

    @Column(nullable = false)
    private LocalDate visitDate;

    @Column(length = 200)
    private String note;

    /** Set when the owner marks the visit done; done visits leave the list. */
    @Column
    private Instant doneAt;
}
