package com.abhedyam.model;

import com.abhedyam.model.enums.Subscription;
import com.abhedyam.model.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "owners")
@Getter
@Setter
@PrimaryKeyJoinColumn(name = "id")
public class Owner extends User {
    
    @Column(nullable = false)
    private String businessName;
    
    @Column(nullable = false)
    private Boolean isVerified = false;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Subscription subscription = Subscription.GO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;
    
    @Column
    private String subscriptionId;
    
    @Column
    private Instant validTill;

    @Column(unique = true, length = 80)
    private String publicSlug;

    @Column(nullable = false)
    private Boolean publicListingEnabled = false;
}

