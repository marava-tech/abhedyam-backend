package com.abhedyam.service;

import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.repository.FcmDetailsRepository;
import com.abhedyam.repository.NotificationRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.util.PackageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Once a day per owner, at their digest time: how much is due. Only owners with the business app
 * installed (an FCM token) and the digest turned on. The dispatcher dedupes on the date, so a
 * restart or a late tick never sends twice, and moves it past quiet hours.
 * ponytail: every 15 min we recompute for owners with nothing due yet; fine for a few hundred owners,
 * cache a "nothing due today" marker if it gets slow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DueDigestJob {

    private final FcmDetailsRepository fcmDetails;
    private final INotificationSettingsService settings;
    private final NotificationRepository notifications;
    private final SaleItemRepository saleItems;
    private final PaymentRepository payments;
    private final NotificationDispatcher dispatcher;

    @Scheduled(cron = "0 */15 * * * *")
    public void run() {
        run(Instant.now());
    }

    void run(Instant now) {
        for (UUID ownerId : fcmDetails.findUserIdsByPackageName(PackageConstants.BUSINESS_APP_PACKAGE)) {
            try {
                sendIfDue(ownerId, now);
            } catch (Exception e) {
                log.error("Due digest failed for owner {}: {}", ownerId, e.getMessage(), e);
            }
        }
    }

    private void sendIfDue(UUID ownerId, Instant now) {
        NotificationSettings s = settings.forOwner(ownerId);
        if (s.getMutedCategories().contains(NotificationCategory.DUE_DIGEST)) return;
        ZonedDateTime local = now.atZone(ZoneId.of(s.getTimezone()));
        if (local.toLocalTime().isBefore(s.getDigestTime())) return;
        String key = "digest:" + local.toLocalDate();
        if (notifications.findByOwnerIdAndDedupeKey(ownerId, key).isPresent()) return;

        DueDigest digest = DueDigest.of(toMap(saleItems.sumTotalsByCustomer(ownerId)), toMap(payments.sumSuccessfulByCustomer(ownerId)),
            saleItems.findByOwnerId(ownerId), now);
        if (digest == null) return;
        dispatcher.notify(ownerId, NotificationCategory.DUE_DIGEST, NotificationType.INFO, "Money to collect", digest.text(), "DIGEST", null, key);
    }

    private static Map<UUID, BigDecimal> toMap(java.util.List<Object[]> rows) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put(UUID.fromString(r[0].toString()), new BigDecimal(r[1].toString())); // same parsing as LocationDetailsService: row types vary by driver
        return m;
    }
}
