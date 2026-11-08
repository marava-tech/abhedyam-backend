package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** The founder phoned / WhatsApped a trial shop. Stage = which point of the trial (DAY_3, DAY_20, ENDING, ENDED). */
@Entity
@Table(name = "founder_calls", indexes = @Index(name = "idx_founder_calls_owner", columnList = "owner_id"))
@Getter
@Setter
public class FounderCall extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "owner_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private String stage;

    @Column(length = 500)
    private String note;
}
