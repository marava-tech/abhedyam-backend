package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "owner_settings", uniqueConstraints = @UniqueConstraint(columnNames = "owner_id"))
@Getter
@Setter
public class OwnerSettings extends BaseEntity {
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, unique = true, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;
    
    @Column(nullable = false)
    private Boolean dailyQuoteEnabled = true;
    
    @Column(nullable = false)
    private Boolean isDarkModeEnabled = false;
    
    /** When the owner last asked for a data export (limits requests to one per week). */
    @Column
    @jakarta.persistence.Convert(converter = com.abhedyam.config.IstInstantConverter.class)
    private java.time.Instant lastExportRequestedAt;

    /** Villages view: group customers by village, plan village visits. null = off (the default). */
    @Column
    private Boolean villageModeEnabled;

    /** Location tools: map pins, directions, nearby customers, visit route. null = on (the default). */
    @Column
    private Boolean locationToolsEnabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> otherFlags;
}

