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
@Table(name = "stock_purchases", indexes = @Index(name = "idx_stock_purchase_owner", columnList = "ownerId,boughtOn"))
@Getter
@Setter
public class StockPurchase extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @Column(length = 60)
    private String supplier;

    @Column(nullable = false)
    private LocalDate boughtOn;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPaid;

    @Column(length = 200)
    private String note;
}
