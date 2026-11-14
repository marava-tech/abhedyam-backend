package com.abhedyam.service;

import com.abhedyam.model.Owner;

import java.time.Instant;

/**
 * Shop (₹699/yr, also the trial) or Business (₹1,299/yr). Limits per shop. Spec: specs/billing.md,
 * plan: abhedyam-owner/.product/BUSINESS_PLAN.md
 */
public enum Plan {
    SHOP(1, 2),
    BUSINESS(10, 10);

    /** Google Play subscription id of the Business plan; RevenueCat sends "abhedyam_business:yearly". */
    public static final String BUSINESS_PRODUCT = "abhedyam_business";

    /** Phones the owner can be signed in on at once (helpers' phones not counted). */
    public final int phones;
    /** Family members + helpers (joined or invited). */
    public final int helpers;

    Plan(int phones, int helpers) {
        this.phones = phones;
        this.helpers = helpers;
    }

    /** Business only while a Business subscription is paid up; trial, Shop and read-only shops get Shop limits. */
    public static Plan of(Owner owner) {
        boolean active = owner.getAccessUntil() != null && owner.getAccessUntil().isAfter(Instant.now());
        String product = owner.getPlayProductId();
        return active && product != null && product.startsWith(BUSINESS_PRODUCT) ? BUSINESS : SHOP;
    }
}
