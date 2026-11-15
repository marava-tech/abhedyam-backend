package com.abhedyam.model;

import com.abhedyam.model.enums.SaleItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale_items", indexes = {
    @Index(name = "idx_sale_items_owner_id", columnList = "owner_id"),
    @Index(name = "idx_sale_items_customer_id", columnList = "customer_id"),
    @Index(name = "idx_sale_items_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_sale_items_created_at", columnList = "created_at"),
    @Index(name = "idx_sale_items_owner_client_key", columnList = "owner_id, client_key")
})
@Getter
@Setter
public class SaleItem extends BaseEntity {
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID productId;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID customerId;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;
    
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;
    
    @Column(precision = 15, scale = 2)
    private BigDecimal remainingAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SaleItemStatus status = SaleItemStatus.NOT_PAID;
    
    @Column
    private Instant dueDate;
    
    @Column
    private String transactionId;

    /** The phone's id for the New sale that made this row (all rows of one sale share it). Kept forever, so a sale
     *  saved offline and sent again later is never recorded twice. Null = older app. */
    @Column(name = "client_key", length = 64)
    private String clientKey;
}

