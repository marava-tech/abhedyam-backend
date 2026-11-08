package com.abhedyam.service;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Referral;
import com.abhedyam.model.enums.ReferralStatus;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.ReferralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The money paths: Play webhook → access, first payment → referral reward, code rules. */
class BillingServiceTest {

    private final OwnerRepository owners = mock(OwnerRepository.class);
    private final ReferralRepository referrals = mock(ReferralRepository.class);
    private final ReferralService referralService = new ReferralService(owners, referrals);
    private final BillingService billing = new BillingService(owners, referralService, mock(NotificationDispatcher.class));

    private Owner friend;
    private Owner referrer;

    @BeforeEach
    void setUp() {
        friend = owner();
        referrer = owner();
        referrer.setReferralCode("ABHTEST1");
        when(owners.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));
        when(referrals.save(any(Referral.class))).thenAnswer(i -> i.getArgument(0));
        when(owners.findByReferralCode("ABHTEST1")).thenReturn(Optional.of(referrer));
    }

    @Test
    void firstPaymentSetsAccessAndRewardsReferrerWithDays() {
        Instant referrerEnd = referrer.getAccessUntil();
        referralService.apply(friend.getId(), " abhtest1 ");
        assertEquals(referrer.getId().toString(), friend.getReferredBy());
        assertTrue(friend.getAccessUntil().isAfter(Instant.now().plus(44, ChronoUnit.DAYS)), "30-day trial + 15 bonus days");

        Referral r = new Referral();
        r.setReferrerOwnerId(referrer.getId());
        r.setRefereeOwnerId(friend.getId());
        when(referrals.findByRefereeOwnerId(friend.getId())).thenReturn(Optional.of(r));

        Instant playEnd = Instant.now().plus(365, ChronoUnit.DAYS);
        assertTrue(billing.applyRevenueCatEvent(event("INITIAL_PURCHASE", friend, playEnd, "NORMAL")));
        assertEquals(playEnd.toEpochMilli(), friend.getAccessUntil().toEpochMilli());
        assertEquals("abhedyam_yearly", friend.getPlayProductId());
        assertNotNull(friend.getFirstPaidAt());
        assertEquals(ReferralStatus.PAID, r.getStatus());
        assertFalse(r.getRewardCash());
        assertEquals(referrerEnd.plus(30, ChronoUnit.DAYS), referrer.getAccessUntil());

        // RevenueCat retries: a second delivery must not reward again or shorten access
        Instant paidAt = friend.getFirstPaidAt();
        billing.applyRevenueCatEvent(event("RENEWAL", friend, playEnd.minus(5, ChronoUnit.DAYS), "NORMAL"));
        assertEquals(playEnd.toEpochMilli(), friend.getAccessUntil().toEpochMilli());
        assertEquals(paidAt, friend.getFirstPaidAt());
        assertEquals(referrerEnd.plus(30, ChronoUnit.DAYS), referrer.getAccessUntil());
    }

    @Test
    void payingReferrerGetsCashNotDays() {
        referrer.setPlayProductId("abhedyam_yearly");
        Instant referrerEnd = referrer.getAccessUntil();
        Referral r = new Referral();
        r.setReferrerOwnerId(referrer.getId());
        r.setRefereeOwnerId(friend.getId());
        when(referrals.findByRefereeOwnerId(friend.getId())).thenReturn(Optional.of(r));

        billing.applyRevenueCatEvent(event("INITIAL_PURCHASE", friend, Instant.now().plus(30, ChronoUnit.DAYS), "NORMAL"));
        assertTrue(r.getRewardCash());
        assertEquals(referrerEnd, referrer.getAccessUntil());
    }

    @Test
    void trialPeriodEventIsNotAPayment() {
        billing.applyRevenueCatEvent(event("INITIAL_PURCHASE", friend, Instant.now().plus(40, ChronoUnit.DAYS), "TRIAL"));
        assertNull(friend.getFirstPaidAt());
        verify(referrals, never()).findByRefereeOwnerId(any());
    }

    @Test
    void expirationStopsPayingAndUnknownOwnerIsIgnored() {
        friend.setPlayProductId("abhedyam_monthly");
        billing.applyRevenueCatEvent(event("EXPIRATION", friend, null, null));
        assertNull(friend.getPlayProductId());

        Map<String, Object> anon = new HashMap<>(Map.of("type", "RENEWAL", "app_user_id", "$RCAnonymousID:abc"));
        assertFalse(billing.applyRevenueCatEvent(anon));
    }

    @Test
    void codeRules() {
        assertEquals("SELF_REFERRAL", assertThrows(BusinessException.class,
                () -> referralService.apply(referrer.getId(), "ABHTEST1")).getErrorCode());
        friend.setFirstPaidAt(Instant.now());
        assertEquals("ALREADY_PAID", assertThrows(BusinessException.class,
                () -> referralService.apply(friend.getId(), "ABHTEST1")).getErrorCode());
    }

    private Owner owner() {
        Owner o = new Owner();
        o.setId(UUID.randomUUID());
        o.setAccessUntil(Instant.now().plus(BillingService.TRIAL_DAYS, ChronoUnit.DAYS));
        when(owners.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private static Map<String, Object> event(String type, Owner o, Instant expiry, String periodType) {
        Map<String, Object> e = new HashMap<>();
        e.put("type", type);
        e.put("app_user_id", o.getId().toString());
        e.put("product_id", "abhedyam_yearly");
        if (expiry != null) e.put("expiration_at_ms", expiry.toEpochMilli());
        if (periodType != null) e.put("period_type", periodType);
        return e;
    }
}
