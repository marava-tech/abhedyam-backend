package com.abhedyam.model;

import com.abhedyam.model.enums.StockMovementType;
import com.abhedyam.model.enums.StockRemovalReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stock_movements", indexes = @Index(name = "idx_stock_movement_product", columnList = "ownerId,productId,createdAt"))
@Getter
@Setter
public class StockMovement extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMovementType type;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal stockAfter;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StockRemovalReason reason;

    @Column(length = 200)
    private String note;

    @Column(length = 64)
    private String saleTransactionId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID purchaseId;
}
