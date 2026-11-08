package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** ₹ owed to a partner for one shop's first payment. paidAt set when the founder pays it out. */
@Entity
@Table(name = "partner_earnings", indexes = @Index(name = "idx_partner_earnings_partner", columnList = "partner_id"))
@Getter
@Setter
public class PartnerEarning extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "partner_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID partnerId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "owner_id", nullable = false, unique = true, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @Column(nullable = false)
    private Integer rupees;

    @Column
    private Instant paidAt;
}
