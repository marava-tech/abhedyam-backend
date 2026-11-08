package com.abhedyam.service;

import com.abhedyam.model.Owner;
import com.abhedyam.model.Subscription;
import com.abhedyam.model.enums.SubscriptionStatus;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryJob {

    private final OwnerRepository ownerRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 0 */12 * * ?")
    @Transactional
    public void expireEligibleSubscriptions() {
        Instant now = Instant.now();
        List<Owner> eligible = ownerRepository.findBySubscriptionStatusAndValidTillBefore(SubscriptionStatus.ACTIVE, now);
        if (eligible.isEmpty()) {
            log.debug("No subscriptions to expire");
            return;
        }
        log.info("Expiring {} subscription(s) with validTill before {}", eligible.size(), now);
        for (Owner owner : eligible) {
            try {
                owner.setSubscription(com.abhedyam.model.enums.Subscription.SAAS);
                owner.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
                owner.setValidTill(null);
                owner.setSubscriptionId(null);
                ownerRepository.save(owner);
                subscriptionRepository.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
                        .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                        .findFirst()
                        .ifPresent(sub -> {
                            sub.setStatus(SubscriptionStatus.EXPIRED);
                            sub.setExpiredAt(now);
                            subscriptionRepository.save(sub);
                        });
                log.info("Expired subscription for ownerId: {}", owner.getId());
            } catch (Exception e) {
                log.error("Error expiring subscription for ownerId: {}", owner.getId(), e);
            }
        }
    }
}
