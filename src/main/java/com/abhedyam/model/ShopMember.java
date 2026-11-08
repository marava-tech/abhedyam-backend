package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** A family member or helper of a shop. Invite and member are one row: INVITED → ACTIVE (on join) → REMOVED. */
@Entity
@Table(name = "shop_members", indexes = {
        @Index(name = "idx_shop_members_owner", columnList = "owner_id"),
        @Index(name = "idx_shop_members_code", columnList = "invite_code", unique = true),
        @Index(name = "idx_shop_members_uid", columnList = "firebase_uid")})
@Getter
@Setter
public class ShopMember extends BaseEntity {
    public enum Role { FAMILY, HELPER }
    public enum Status { INVITED, ACTIVE, REMOVED }

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "owner_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.INVITED;

    @Column(name = "invite_code", length = 6)
    private String inviteCode;

    private Instant inviteExpiresAt;

    @Column(length = 254)
    private String email;

    @Column(name = "firebase_uid", length = 128)
    private String firebaseUid;

    private Instant joinedAt;

    private Instant removedAt;
}
