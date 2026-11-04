package com.abhedyam.service;

import com.abhedyam.model.Notification;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.enums.DeliveryStatus;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.repository.NotificationRepository;
import com.abhedyam.service.interfaces.IFcmService;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.util.PackageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * The one way to tell an owner something. Always writes the in-app row; the push is skipped if the
 * owner muted the category, and held until quiet hours end if it is night. Nobody else calls FCM for owners.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final NotificationRepository notifications;
    private final INotificationSettingsService settings;
    private final IFcmService fcm;

    @Transactional
    public Notification notify(UUID ownerId, NotificationCategory category, NotificationType type, String title, String body,
                               String relatedType, UUID relatedId, String dedupeKey) {
        return notify(ownerId, category, type, title, body, relatedType, relatedId, dedupeKey, Instant.now());
    }

    Notification notify(UUID ownerId, NotificationCategory category, NotificationType type, String title, String body,
                        String relatedType, UUID relatedId, String dedupeKey, Instant now) {
        if (dedupeKey != null) {
            var existing = notifications.findByOwnerIdAndDedupeKey(ownerId, dedupeKey);
            if (existing.isPresent()) return existing.get();
        }
        Notification n = new Notification();
        n.setOwnerId(ownerId);
        n.setUserId(ownerId);
        n.setType(type);
        n.setCategory(category);
        n.setTitle(title);
        n.setMessage(body);
        n.setTimestamp(now);
        n.setRelatedEntityType(relatedType);
        n.setRelatedEntityId(relatedId);
        n.setDedupeKey(dedupeKey);

        NotificationSettings s = settings.forOwner(ownerId);
        if (s.getMutedCategories().contains(category)) {
            n.setDeliveryStatus(DeliveryStatus.SKIPPED);
            return notifications.save(n);
        }
        Instant at = QuietHours.nextSendTime(now, s.getQuietStart(), s.getQuietEnd(), ZoneId.of(s.getTimezone()));
        n.setPushAfter(at);
        if (at.isAfter(now)) {
            n.setDeliveryStatus(DeliveryStatus.DEFERRED);
        } else {
            push(n);
            n.setDeliveryStatus(DeliveryStatus.SENT);
        }
        return notifications.save(n);
    }

    /** Sends pushes that were held for quiet hours. ponytail: single instance, no row locking; add it if we scale out. */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void deliverDeferred() {
        deliverDeferred(Instant.now());
    }

    void deliverDeferred(Instant now) {
        for (Notification n : notifications.findByDeliveryStatusAndPushAfterLessThanEqual(DeliveryStatus.DEFERRED, now)) {
            try {
                boolean muted = settings.forOwner(n.getOwnerId()).getMutedCategories().contains(n.getCategory());
                if (muted) {
                    n.setDeliveryStatus(DeliveryStatus.SKIPPED);
                } else {
                    push(n);
                    n.setDeliveryStatus(DeliveryStatus.SENT);
                }
            } catch (Exception e) {
                log.error("Deferred push failed for notification {}: {}", n.getId(), e.getMessage());
                n.setDeliveryStatus(DeliveryStatus.FAILED);
            }
            notifications.save(n);
        }
    }

    private void push(Notification n) {
        fcm.sendNotificationToUser(n.getOwnerId(), n.getTitle(), n.getMessage(), PackageConstants.BUSINESS_APP_PACKAGE);
    }
}
