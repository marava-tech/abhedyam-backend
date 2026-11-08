package com.abhedyam.model;

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
    
    /** Full access until this time: the free trial, a Play subscription period, or referral days. After it the app is read-only. */
    @Column
    private Instant accessUntil;

    /** Google Play product the owner pays for; null while on trial or after it lapsed. */
    @Column(length = 80)
    private String playProductId;

    /** First real Play payment (not a trial). Counts founding seats and triggers the referrer's reward. */
    @Column
    private Instant firstPaidAt;

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
}

