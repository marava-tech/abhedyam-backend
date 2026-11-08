package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One signed-in phone. Its id is the `sid` claim in that phone's JWT; revoking it logs that phone out on its next request. */
@Entity
@Table(name = "owner_sessions", indexes = @Index(name = "idx_owner_sessions_owner", columnList = "owner_id"))
@Getter
@Setter
public class OwnerSession extends BaseEntity {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "owner_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    /** Random id the app keeps per install (X-Device-Id). Null from app versions before 1.13. */
    @Column(length = 64)
    private String deviceId;

    /** e.g. "Vivo V2250" (X-Device-Name). */
    @Column(length = 100)
    private String deviceName;

    /** Family member / helper signed in on this phone; null = the owner. */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "member_id", columnDefinition = "VARCHAR(36)")
    private UUID memberId;

    private Instant lastSeenAt;

    private Instant revokedAt;
}
