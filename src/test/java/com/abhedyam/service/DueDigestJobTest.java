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
    @Mock com.abhedyam.service.interfaces.IPaymentDueService paymentDues;
    @Mock com.abhedyam.service.interfaces.IOwnerSettingsService ownerSettings;
    @Mock com.abhedyam.service.interfaces.IItemService items;
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
        lenient().when(saleItems.oldestUnpaidSaleByCustomer(owner)).thenReturn(List.of());
        lenient().when(paymentDues.ownerDues(owner)).thenReturn(List.of());
    }

    final UUID dueId = UUID.randomUUID();

    com.abhedyam.dto.OwnerDueResponse due(java.time.LocalDate date, String status, Integer remind) {
        var d = new com.abhedyam.dto.PaymentDueResponse(dueId, new java.math.BigDecimal("5000"), java.math.BigDecimal.ZERO,
            new java.math.BigDecimal("5000"), date, "after harvest", status, Instant.EPOCH, remind, null);
        return new com.abhedyam.dto.OwnerDueResponse(customer, "Prava", null, "Warangal", d);
    }

    @Test
    void everyUnpaidDueIsRemindedTheDayBefore() {
        var tomorrow = java.time.LocalDate.of(2026, 11, 5);
        when(paymentDues.ownerDues(owner)).thenReturn(List.of(due(tomorrow, "OPEN", null)));
        job.run(ist(8, 15));
        verify(dispatcher).notify(eq(owner), eq(NotificationCategory.OWNER_REMINDER), eq(NotificationType.INFO), Texts.says("Prava pays tomorrow"),
            Texts.says("₹5,000 due on Thu, 5 Nov · after harvest"), eq("DUE"), eq(dueId), eq("due-remind:" + dueId + ":2026-11-05:1"));

        reset(dispatcher);
        when(paymentDues.ownerDues(owner)).thenReturn(List.of(due(tomorrow, "PAID", null),
            due(tomorrow.plusDays(1), "OPEN", null), due(tomorrow.plusDays(1), "OPEN", 7)));
        job.run(ist(8, 15));
        verify(dispatcher, never()).notify(any(), eq(NotificationCategory.OWNER_REMINDER), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sevenDaysAheadSaysInSevenDays() {
        when(paymentDues.ownerDues(owner)).thenReturn(List.of(due(java.time.LocalDate.of(2026, 11, 11), "OPEN", 7)));
        job.run(ist(8, 15));
        verify(dispatcher).notify(eq(owner), eq(NotificationCategory.OWNER_REMINDER), any(), Texts.says("Prava pays in 7 days"), any(), any(), any(),
            eq("due-remind:" + dueId + ":2026-11-11:7"));
    }

    @Test
    void villageVisitIsRemindedTheDayBefore() {
        UUID visit = UUID.randomUUID();
        when(ownerSettings.villageMode(owner)).thenReturn(true);
        when(paymentDues.ownerVisits(owner)).thenReturn(List.of(new com.abhedyam.dto.CollectionVisitResponse(visit, "Hanamkonda",
            java.time.LocalDate.of(2026, 11, 5), null, "UPCOMING", 6, new java.math.BigDecimal("18400"))));
        job.run(ist(8, 15));
        verify(dispatcher).notify(eq(owner), eq(NotificationCategory.OWNER_REMINDER), any(), Texts.says("Visit Hanamkonda tomorrow"),
            Texts.says("6 customers owe ₹18,400"), eq("VISIT"), eq(visit), eq("visit-remind:" + visit + ":2026-11-05"));
    }

    @Test
    void noVillageVisitRemindersWhenVillagesViewIsOff() {
        when(ownerSettings.villageMode(owner)).thenReturn(false);
        job.run(ist(8, 15));
        verify(paymentDues, never()).ownerVisits(any());
        verify(dispatcher, never()).notify(any(), any(), any(), any(), any(), eq("VISIT"), any(), any());
    }

    @Test
    void digestLeadsWithTodaysAndOverdueDues() {
        var today = java.time.LocalDate.of(2026, 11, 4);
        when(paymentDues.ownerDues(owner)).thenReturn(List.of(due(today, "OPEN", null), due(today.minusDays(3), "OVERDUE", null)));
        job.run(ist(8, 15));
        verify(dispatcher).notify(eq(owner), eq(NotificationCategory.DUE_DIGEST), any(), any(),
            Texts.startsWith("1 due today ₹5,000 · 1 overdue ₹5,000 · 1 customer owes ₹500"), any(), any(), any());
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

    static com.abhedyam.dto.ItemResponse item(String name, String stock, com.abhedyam.dto.ItemResponse.Status status) {
        return new com.abhedyam.dto.ItemResponse(UUID.randomUUID(), name, java.math.BigDecimal.TEN, null, com.abhedyam.model.enums.StockUnit.KG,
            true, new java.math.BigDecimal(stock), java.math.BigDecimal.ONE, status, null, null, null, java.math.BigDecimal.ZERO, null, null, false);
    }

    @Test
    void digestEndsWithWhatToBuy() {
        when(items.all(owner)).thenReturn(List.of(item("Rice", "0.5", com.abhedyam.dto.ItemResponse.Status.LOW),
            item("Oil", "0", com.abhedyam.dto.ItemResponse.Status.OUT), item("Salt", "9", com.abhedyam.dto.ItemResponse.Status.OK),
            item("Dal", "1", com.abhedyam.dto.ItemResponse.Status.LOW)));
        job.run(ist(8, 15));
        verify(dispatcher).notify(eq(owner), eq(NotificationCategory.DUE_DIGEST), any(), Texts.says("Money to collect"),
            Texts.says("1 customer owes ₹500 · oldest 0 days · Buy soon: Oil (out), Dal (1 kg left) +1 more"), any(), any(), any());
    }

    @Test
    void stockAloneStillSendsADigestAndAStockErrorNeverBlocksIt() {
        when(saleItems.sumTotalsByCustomer(owner)).thenReturn(List.of());
        when(items.all(owner)).thenReturn(List.of(item("Oil", "0", com.abhedyam.dto.ItemResponse.Status.OUT)));
        job.run(ist(8, 15));
        verify(dispatcher).notify(eq(owner), eq(NotificationCategory.DUE_DIGEST), any(), Texts.says("Stock running low"), Texts.says("Buy soon: Oil (out)"), any(), any(), any());

        reset(dispatcher);
        when(saleItems.sumTotalsByCustomer(owner)).thenReturn(java.util.Collections.singletonList(new Object[] {customer.toString(), new java.math.BigDecimal("500.00")}));
        when(items.all(owner)).thenThrow(new RuntimeException("boom"));
        job.run(ist(8, 15));
        verify(dispatcher).notify(eq(owner), eq(NotificationCategory.DUE_DIGEST), any(), Texts.says("Money to collect"), Texts.says("1 customer owes ₹500 · oldest 0 days"), any(), any(), any());
    }
}
