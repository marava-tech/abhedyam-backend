package com.abhedyam.model;

import com.abhedyam.model.enums.ReminderChannel;
import com.abhedyam.model.enums.ReminderStatus;
import com.abhedyam.model.enums.ReminderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reminders")
@Getter
@Setter
public class Reminder extends BaseEntity {
    
    @Column(nullable = false)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReminderType type;
    
    @Convert(converter = ReminderChannelConverter.class)
    @Column(nullable = false, length = 50)
    private ReminderChannel channel = ReminderChannel.IN_APP;
    
    @Column(nullable = false)
    private Instant time;
    
    @Column(nullable = false)
    private String text;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(
            columnDefinition = "VARCHAR(36)")
    private UUID customerId;
    
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID ownerId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReminderStatus status = ReminderStatus.PENDING;
    
    @ElementCollection
    @CollectionTable(name = "reminder_packages", joinColumns = @JoinColumn(name = "reminder_id"))
    @Column(name = "package_name")
    private java.util.List<String> packages;
}

