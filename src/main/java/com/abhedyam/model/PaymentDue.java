package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A promised part-payment of a customer's balance. How much of it is paid is computed (DueAllocator), not stored. */
@Entity
@Table(name = "payment_dues", indexes = @Index(name = "idx_payment_dues_customer", columnList = "customerId"))
@Getter
@Setter
public class PaymentDue extends BaseEntity {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID customerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(length = 200)
    private String note;

    /** Every due gets a push the day before; this adds one more this many days before (2-30). Null = none. */
    @Column
    private Integer earlyReminderDays;
}
