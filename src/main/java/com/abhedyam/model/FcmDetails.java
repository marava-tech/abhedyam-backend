package com.abhedyam.model;

import com.abhedyam.model.enums.AppLanguage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "fcm_details", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"userId", "token", "packageName"})
})
@Getter
@Setter
public class FcmDetails extends BaseEntity {
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID userId;
    
    @Column(nullable = false, length = 500)
    private String token;
    
    @Column(nullable = false, length = 200)
    private String packageName;
    
    @Column(length = 100)
    private String deviceId;
    
    @Column(length = 50)
    private String deviceType;

    /** The app language on this phone: its pushes are written in it. null (older app) = English. */
    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private AppLanguage language;
}

