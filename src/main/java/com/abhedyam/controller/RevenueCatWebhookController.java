package com.abhedyam.controller;

import com.abhedyam.model.Owner;
import com.abhedyam.model.Subscription;
import com.abhedyam.model.enums.SubscriptionStatus;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.SubscriptionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Billing", description = "RevenueCat webhook and Google Play billing sync")
public class RevenueCatWebhookController {

    private final OwnerRepository ownerRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Value("${revenuecat.webhook.auth:}")
    private String expectedAuthToken;

    private static final Set<String> GRANT_EVENTS = Set.of(
            "INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION",
            "NON_RENEWING_PURCHASE", "CANCELLATION", "BILLING_ISSUE", "SUBSCRIPTION_EXTENDED"
    );

    @PostMapping("/revenuecat")
    @Operation(summary = "RevenueCat webhook listener", description = "Synchronizes Google Play subscription lifecycle into Abhedyam")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (expectedAuthToken != null && !expectedAuthToken.trim().isEmpty()) {
            if (authorization == null || !authorization.equals(expectedAuthToken)) {
                log.warn("Unauthorized RevenueCat webhook attempt with auth: {}", authorization);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) body.get("event");
        if (event == null) {
            return ResponseEntity.ok(Map.of("received", true, "applied", false));
        }

        String appUserId = (String) event.get("app_user_id");
        if (appUserId == null || appUserId.startsWith("$RCAnonymousID")) {
            log.info("Ignoring anonymous RevenueCat event for app_user_id: {}", appUserId);
            return ResponseEntity.ok(Map.of("received", true, "applied", false));
        }

        UUID ownerId;
        try {
            ownerId = UUID.fromString(appUserId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in RevenueCat app_user_id: {}", appUserId);
            return ResponseEntity.ok(Map.of("received", true, "applied", false));
        }

        Optional<Owner> ownerOpt = ownerRepository.findById(ownerId);
        if (ownerOpt.isEmpty()) {
            log.warn("Owner not found for RevenueCat webhook: {}", ownerId);
            return ResponseEntity.ok(Map.of("received", true, "applied", false));
        }

        Owner owner = ownerOpt.get();
        String type = (String) event.get("type");
        Number expMs = (Number) event.get("expiration_at_ms");
        String productId = (String) event.get("product_id");

        log.info("Processing RevenueCat event type: {} for owner: {}, productId: {}", type, ownerId, productId);

        if ("EXPIRATION".equals(type)) {
            owner.setSubscription(com.abhedyam.model.enums.Subscription.GO);
            owner.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            owner.setValidTill(Instant.now());
            ownerRepository.save(owner);

            updateActiveSubscriptionStatus(ownerId, SubscriptionStatus.EXPIRED, Instant.now());
            log.info("Subscription marked expired for owner: {}", ownerId);
        } else if (GRANT_EVENTS.contains(type) && expMs != null) {
            Instant expiry = Instant.ofEpochMilli(expMs.longValue());

            owner.setSubscription(com.abhedyam.model.enums.Subscription.PRO);
            owner.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            owner.setValidTill(expiry);
            if (productId != null) {
                owner.setSubscriptionId(productId);
            }
            ownerRepository.save(owner);

            saveOrUpdateSubscription(ownerId, productId != null ? productId : "GOOGLE_PLAY", expiry);
            log.info("Subscription activated/extended for owner: {}, validTill: {}", ownerId, expiry);
        }

        return ResponseEntity.ok(Map.of("ok", true, "applied", true));
    }

    private void saveOrUpdateSubscription(UUID ownerId, String orderId, Instant validTill) {
        List<Subscription> subs = subscriptionRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        Optional<Subscription> active = subs.stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .findFirst();

        if (active.isPresent()) {
            Subscription s = active.get();
            s.setValidTill(validTill);
            subscriptionRepository.save(s);
        } else {
            Subscription s = new Subscription();
            s.setOwnerId(ownerId);
            s.setRazorpayOrderId("RC_" + UUID.randomUUID().toString().substring(0, 8));
            s.setStatus(SubscriptionStatus.ACTIVE);
            s.setActivatedAt(Instant.now());
            s.setValidTill(validTill);
            subscriptionRepository.save(s);
        }
    }

    private void updateActiveSubscriptionStatus(UUID ownerId, SubscriptionStatus status, Instant expiredAt) {
        subscriptionRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .findFirst()
                .ifPresent(s -> {
                    s.setStatus(status);
                    s.setExpiredAt(expiredAt);
                    subscriptionRepository.save(s);
                });
    }
}
