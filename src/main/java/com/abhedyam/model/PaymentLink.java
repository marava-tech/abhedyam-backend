package com.abhedyam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_links")
@Getter
@Setter
public class PaymentLink extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID customerId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID saleItemId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column
    @Convert(converter = com.abhedyam.config.IstInstantConverter.class)
    private Instant expiresAt;
}
