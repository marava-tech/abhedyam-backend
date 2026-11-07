package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.abhedyam.model.enums.StockUnit;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = {"ownerId", "code"}))
@Getter
@Setter
public class Product extends BaseEntity {
    
    @Column(nullable = false)
    private String code;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @Column
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private StockUnit unit;

    @Column(precision = 15, scale = 2)
    private BigDecimal costPrice;

    @Column
    private Boolean trackStock;

    @Column(precision = 18, scale = 3)
    private BigDecimal lowStockLevel;

    public boolean tracksStock() {
        return Boolean.TRUE.equals(trackStock);
    }
}

