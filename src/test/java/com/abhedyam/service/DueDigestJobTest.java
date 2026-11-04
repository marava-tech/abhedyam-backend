package com.abhedyam.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.abhedyam.model.Notification;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.repository.FcmDetailsRepository;
import com.abhedyam.repository.NotificationRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.util.PackageConstants;
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
class DueDigestJobTest {
    @Mock FcmDetailsRepository fcm;
    @Mock INotificationSettingsService settings;
    @Mock NotificationRepository notifications;
    @Mock SaleItemRepository saleItems;
    @Mock PaymentRepository payments;
    @Mock NotificationDispatcher dispatcher;
    @InjectMocks DueDigestJob job;

    final UUID owner = UUID.randomUUID(), customer = UUID.randomUUID();
    final NotificationSettings s = new NotificationSettings(); // digest 08:00 IST
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    static Instant ist(int h, int m) {
        return ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, IST).toInstant();
    }

    @BeforeEach
    void setUp() {
        lenient().when(fcm.findUserIdsByPackageName(PackageConstants.BUSINESS_APP_PACKAGE)).thenReturn(List.of(owner));
        lenient().when(settings.forOwner(owner)).thenReturn(s);
        lenient().when(notifications.findByOwnerIdAndDedupeKey(any(), any())).thenReturn(Optional.empty());
        lenient().when(saleItems.sumTotalsByCustomer(owner)).thenReturn(java.util.Collections.singletonList(new Object[] {customer.toString(), new java.math.BigDecimal("500.00")}));
        lenient().when(payments.sumSuccessfulByCustomer(owner)).thenReturn(List.of());
        lenient().when(saleItems.findByOwnerId(owner)).thenReturn(List.of());
    }

    @Test
    void sendsOncePastDigestTimeWithADateKey() {
        job.run(ist(8, 15));
        verify(dispatcher).notify(eq(owner), eq(NotificationCategory.DUE_DIGEST), eq(NotificationType.INFO), any(), any(), eq("DIGEST"), any(), eq("digest:2026-11-04"));
    }

    @Test
    void waitsUntilTheOwnersDigestTime() {
        job.run(ist(7, 45));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void alreadySentTodayIsNotSentAgain() {
        when(notifications.findByOwnerIdAndDedupeKey(owner, "digest:2026-11-04")).thenReturn(Optional.of(new Notification()));
        job.run(ist(9, 0));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void ownerWhoTurnedTheDigestOffGetsNothing() {
        s.getMutedCategories().add(NotificationCategory.DUE_DIGEST);
        job.run(ist(9, 0));
        verifyNoInteractions(dispatcher);
    }

    @Test
    void nothingDueSendsNothing() {
        when(saleItems.sumTotalsByCustomer(owner)).thenReturn(List.of());
        job.run(ist(9, 0));
        verifyNoInteractions(dispatcher);
    }
}
