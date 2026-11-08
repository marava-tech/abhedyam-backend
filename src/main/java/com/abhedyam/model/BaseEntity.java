package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    @Convert(converter = com.abhedyam.config.IstInstantConverter.class)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    @Convert(converter = com.abhedyam.config.IstInstantConverter.class)
    private Instant updatedAt;

    @Column
    @Convert(converter = com.abhedyam.config.IstInstantConverter.class)
    private Instant deletedAt;

    @Column(nullable = false)
    private Boolean isActive = true;

    /** Family member / helper who created this row ("by Ravi"); null = the owner or the system. */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "added_by", updatable = false, columnDefinition = "VARCHAR(36)")
    private UUID addedBy;

    @PrePersist
    void stampAddedBy() {
        if (addedBy == null) addedBy = com.abhedyam.util.SecurityUtil.getCurrentMemberId();
    }
}

