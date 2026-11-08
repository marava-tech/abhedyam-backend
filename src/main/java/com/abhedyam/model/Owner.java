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
    private Subscription subscription = Subscription.SAAS;
    
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

    /** The Gmail this shop signed in with, kept after the account is disabled (email itself is freed so the person can sign up again). */
    @Column
    private String formerEmail;

    @Column(unique = true, length = 16)
    private String referralCode;

    @Column(length = 36)
    private String referredBy;

    @Column(length = 100)
    private String deviceHardwareId;
}

