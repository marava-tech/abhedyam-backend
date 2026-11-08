package com.abhedyam.model;

import com.abhedyam.model.enums.ReferralStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One shop that joined with another shop's code. The referrer is rewarded only when the friend pays. */
@Entity
@Table(name = "referrals", indexes = {
    @Index(name = "idx_referrals_referrer", columnList = "referrer_owner_id")
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
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private ReferralStatus status = ReferralStatus.JOINED;

    /** When the friend paid and the reward was given. */
    @Column
    private Instant rewardedAt;

    /** true: the referrer already pays on Play, so the reward is ₹200 by UPI (paid by hand) instead of free days. */
    @Column(nullable = false)
    private Boolean rewardCash = false;
}
