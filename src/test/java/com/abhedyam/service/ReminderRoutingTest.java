package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.abhedyam.model.Reminder;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.model.enums.ReminderStatus;
import com.abhedyam.repository.ReminderRepository;
import com.abhedyam.util.PackageConstants;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReminderRoutingTest {
    @Mock ReminderRepository repo;
    @Mock NotificationService notificationService;
    @Mock FcmService fcm;
    @Mock NotificationDispatcher dispatcher;
    @InjectMocks ReminderSchedulerService scheduler;

    Reminder reminder(UUID owner, UUID customer) {
        Reminder r = new Reminder();
        r.setId(UUID.randomUUID());
        r.setOwnerId(owner);
        r.setCustomerId(customer);
        r.setName("Call Ravi");
        r.setText("Ask about the TV payment");
        r.setStatus(ReminderStatus.PENDING);
        when(repo.findDueReminders(eq(ReminderStatus.PENDING), any(Instant.class))).thenReturn(List.of(r));
        return r;
    }

    @Test
    void ownersOwnReminderGoesThroughTheDispatcher() {
        UUID owner = UUID.randomUUID();
        Reminder r = reminder(owner, null);
        scheduler.processDueReminders();
        verify(dispatcher).notify(owner, NotificationCategory.OWNER_REMINDER, NotificationType.INFO,
            "Call Ravi", "Ask about the TV payment", "REMINDER", r.getId(), "reminder:" + r.getId());
        verifyNoInteractions(fcm, notificationService);
        assertEquals(ReminderStatus.SENT, r.getStatus());
    }

    @Test
    void reminderForACustomerStaysOnTheCustomerPathAndSkipsTheDispatcher() {
        UUID owner = UUID.randomUUID(), customer = UUID.randomUUID();
        reminder(owner, customer);
        scheduler.processDueReminders();
        verify(notificationService).create(any());
        verify(fcm).sendNotificationToUser(eq(customer), eq("Call Ravi"), eq("Ask about the TV payment"), eq(PackageConstants.CUSTOMER_APP_PACKAGE));
        verifyNoInteractions(dispatcher);
    }
}
