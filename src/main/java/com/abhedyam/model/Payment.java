package com.abhedyam.model;

import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_owner_id", columnList = "owner_id"),
    @Index(name = "idx_payments_customer_id", columnList = "customer_id"),
    @Index(name = "idx_payments_sale_item_id", columnList = "sale_item_id"),
    @Index(name = "idx_payments_created_at", columnList = "created_at"),
        @Index(name = "idx_payments_owner_client_key", columnList = "owner_id, client_key")
})
@Getter
@Setter
public class Payment extends BaseEntity {
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID customerId;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID saleItemId;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMedium medium;

    /** Written off by the owner (money that will not come back): lowers the balance, never counts as collected. */
    @Column(name = "write_off")
    private Boolean writeOff;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column
    private String reference;

    /** Due the owner said this pays ("Got paid" on a due); it is filled first. Null = earliest due first. */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID dueId;
    
    /** The phone's id for the Payment in that made this row (all rows of one Payment in share it). Kept forever, so an
     *  entry saved offline and sent again hours later is never recorded twice. Null = older app / not from Payment in. */
    @Column(name = "client_key", length = 64)
    private String clientKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;
}

