package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_customers_owner_id", columnList = "owner_id"),
    @Index(name = "idx_customers_link_token", columnList = "link_token", unique = true)
})
@Getter
@Setter
@PrimaryKeyJoinColumn(name = "id")
public class Customer extends User {
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    /** Owner stopped chasing this customer: left out of Who to chase / Old dues. Balance unchanged. */
    @Column(name = "chase_paused")
    private Boolean chasePaused;

    /** Goes up on every edit. Phones send the number they loaded, so an old edit form can't overwrite a newer edit. */
    @Column(name = "edit_version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private long editVersion;

    /** Private no-login link for the customer (duesetu.com/k/<token>). Reset = new token, old link dead. */
    @Column(name = "link_token", length = 32)
    private String linkToken;

    /** Collection cycle (spec collection-cycle.md): WEEKLY (day 1-7, Mon-Sun) or MONTHLY (day 1-31); all null = none. */
    @Column(name = "cycle_kind", length = 10)
    private String cycleKind;

    @Column(name = "cycle_day")
    private Integer cycleDay;

    @Column(name = "cycle_amount", precision = 15, scale = 2)
    private BigDecimal cycleAmount;

    /** Day the cycle was set: earlier cycle days never count as missed. */
    @Column(name = "cycle_since")
    private LocalDate cycleSince;
}
