package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.abhedyam.model.Notification;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.enums.DeliveryStatus;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.repository.NotificationRepository;
import com.abhedyam.service.interfaces.IFcmService;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {
    @Mock NotificationRepository repo;
    @Mock INotificationSettingsService settings;
    @Mock IFcmService fcm;
    @InjectMocks NotificationDispatcher dispatcher;

    final UUID owner = UUID.randomUUID();
    final NotificationSettings defaults = new NotificationSettings(); // 21:00-08:00 IST
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    final Instant day = ZonedDateTime.of(2026, 11, 4, 14, 0, 0, 0, IST).toInstant();
    final Instant night = ZonedDateTime.of(2026, 11, 4, 23, 0, 0, 0, IST).toInstant();

    @BeforeEach
    void setUp() {
        lenient().when(settings.forOwner(owner)).thenReturn(defaults);
        lenient().when(repo.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
    }

    Notification send(NotificationCategory c, String key, Instant now) {
        return dispatcher.notify(owner, c, NotificationType.INFO, "T", "B", "PAYMENT", UUID.randomUUID(), key, now);
    }

    @Test
    void daytimeWritesRowAndPushesNow() {
        Notification n = send(NotificationCategory.OWNER_REMINDER, null, day);
        assertEquals(DeliveryStatus.SENT, n.getDeliveryStatus());
        verify(fcm).sendNotificationToUser(eq(owner), eq("T"), eq("B"), any(), any());
    }

    @Test
    void pushCarriesWhatTheAppNeedsToOpenTheRightScreen() {
        UUID id = UUID.randomUUID(), entity = UUID.randomUUID();
        Notification n = new Notification();
        n.setId(id);
        n.setCategory(NotificationCategory.PAYMENT_NEEDS_OK);
        n.setRelatedEntityType("PAYMENT");
        n.setRelatedEntityId(entity);
        assertEquals(java.util.Map.of("notificationId", id.toString(), "category", "PAYMENT_NEEDS_OK",
            "relatedEntityType", "PAYMENT", "relatedEntityId", entity.toString()), NotificationDispatcher.dataFor(n));
    }

    @Test
    void dataSkipsMissingFieldsBecauseFcmRejectsNulls() {
        assertTrue(NotificationDispatcher.dataFor(new Notification()).isEmpty());
    }

    @Test
    void nightKeepsRowButHoldsThePush() {
        Notification n = send(NotificationCategory.OWNER_REMINDER, null, night);
        assertEquals(DeliveryStatus.DEFERRED, n.getDeliveryStatus());
        assertTrue(n.getPushAfter().isAfter(night));
        verifyNoInteractions(fcm);
    }

    @Test
    void mutedCategoryKeepsRowButNeverPushes() {
        defaults.getMutedCategories().add(NotificationCategory.DUE_DIGEST);
        Notification n = send(NotificationCategory.DUE_DIGEST, null, day);
        assertEquals(DeliveryStatus.SKIPPED, n.getDeliveryStatus());
        verifyNoInteractions(fcm);
    }

    @Test
    void sameDedupeKeyIsIgnored() {
        Notification first = new Notification();
        when(repo.findByOwnerIdAndDedupeKey(owner, "digest-2026-11-04")).thenReturn(Optional.of(first));
        assertSame(first, send(NotificationCategory.DUE_DIGEST, "digest-2026-11-04", day));
        verify(repo, never()).save(any());
        verifyNoInteractions(fcm);
    }

    @Test
    void deferredPushGoesOutOnceQuietHoursEnd() {
        Notification held = send(NotificationCategory.OWNER_REMINDER, null, night);
        when(repo.findByDeliveryStatusAndPushAfterLessThanEqual(DeliveryStatus.DEFERRED, held.getPushAfter())).thenReturn(List.of(held));
        dispatcher.deliverDeferred(held.getPushAfter());
        assertEquals(DeliveryStatus.SENT, held.getDeliveryStatus());
        verify(fcm, times(1)).sendNotificationToUser(eq(owner), eq("T"), eq("B"), any(), any());
    }

    @Test
    void deferredPushIsDroppedIfOwnerMutedMeanwhile() {
        Notification held = send(NotificationCategory.OWNER_REMINDER, null, night);
        defaults.getMutedCategories().add(NotificationCategory.OWNER_REMINDER);
        when(repo.findByDeliveryStatusAndPushAfterLessThanEqual(DeliveryStatus.DEFERRED, held.getPushAfter())).thenReturn(List.of(held));
        dispatcher.deliverDeferred(held.getPushAfter());
        assertEquals(DeliveryStatus.SKIPPED, held.getDeliveryStatus());
        verifyNoInteractions(fcm);
    }
}
