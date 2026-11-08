package com.abhedyam.model;

import com.abhedyam.model.enums.ReferralStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referrals", indexes = {
    @Index(name = "idx_referrals_referrer", columnList = "referrer_owner_id"),
    @Index(name = "idx_referrals_referee", columnList = "referee_owner_id", unique = true),
    @Index(name = "idx_referrals_status", columnList = "status")
})
@Getter
@Setter
public class Referral extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "referrer_owner_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID referrerOwnerId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "referee_owner_id", nullable = false, columnDefinition = "VARCHAR(36)", unique = true)
    private UUID refereeOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReferralStatus status = ReferralStatus.PENDING;

    @Column(nullable = false)
    private Integer customerCount = 0;

    @Column(nullable = false)
    private Integer activeDaysCount = 0;

    @Column(nullable = false)
    private Integer transactionCount = 0;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalVolume = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer billsSharedCount = 0;

    @Column
    private Instant rewardedAt;

    @Column(nullable = false)
    private Integer rewardDaysGranted = 30;
}
