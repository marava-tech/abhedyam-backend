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
import com.abhedyam.util.Txt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
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
    private final NotificationText text;

    /** [title] and [body] are written in every app language (specs/notifications-telugu.md). */
    @Transactional
    public Notification notify(UUID ownerId, NotificationCategory category, NotificationType type, Txt title, Txt body,
                               String relatedType, UUID relatedId, String dedupeKey) {
        return notify(ownerId, category, type, title, body, relatedType, relatedId, dedupeKey, Instant.now());
    }

    Notification notify(UUID ownerId, NotificationCategory category, NotificationType type, Txt title, Txt body,
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
        n.setLocalized(text.renderAll(title, body));
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
            return notifications.save(n);
        }
        n.setDeliveryStatus(DeliveryStatus.SENT);
        n = notifications.save(n); // saved first so the push can carry the row id
        push(n);
        return n;
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
        fcm.sendNotificationToUser(n.getOwnerId(), n::textIn, PackageConstants.BUSINESS_APP_PACKAGE, dataFor(n)); // each phone in its language
    }

    /** What the app needs to open the right screen when the push is tapped. FCM data values must be strings, never null. */
    static Map<String, String> dataFor(Notification n) {
        Map<String, String> d = new HashMap<>();
        if (n.getId() != null) d.put("notificationId", n.getId().toString());
        if (n.getCategory() != null) d.put("category", n.getCategory().name());
        if (n.getRelatedEntityType() != null) d.put("relatedEntityType", n.getRelatedEntityType());
        if (n.getRelatedEntityId() != null) d.put("relatedEntityId", n.getRelatedEntityId().toString());
        return d;
    }
}
