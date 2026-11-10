package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "location_details", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@Setter
public class LocationDetails extends BaseEntity {
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, unique = true, columnDefinition = "VARCHAR(36)")
    private UUID userId;
    
    /** The map pin; null when only a village is known (village = grouping label, pin = Location tools). */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;
    
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;
    
    @Column
    private String village;

    /** Hidden cross-script search key for {@link #village} (specs/cross-script-search.md). */
    @Column(name = "village_key")
    private String villageKey;

    @PrePersist
    @PreUpdate
    void keyVillage() {
        villageKey = com.abhedyam.util.SearchKey.of(village);
    }
    
    @Column(columnDefinition = "TEXT")
    private String addressText;
}

