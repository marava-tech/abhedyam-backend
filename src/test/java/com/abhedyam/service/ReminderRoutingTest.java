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
            com.abhedyam.util.Txt.raw("Call Ravi"), com.abhedyam.util.Txt.raw("Ask about the TV payment"), "REMINDER", r.getId(), "reminder:" + r.getId());
        assertEquals(ReminderStatus.SENT, r.getStatus());
    }

    @Test
    void reminderAboutACustomerAlsoReachesTheOwner() {
        // The Reminders tool always attaches a customer; with no customer app it must still reach the owner.
        UUID owner = UUID.randomUUID(), customer = UUID.randomUUID();
        Reminder r = reminder(owner, customer);
        scheduler.processDueReminders();
        verify(dispatcher).notify(owner, NotificationCategory.OWNER_REMINDER, NotificationType.INFO,
            com.abhedyam.util.Txt.raw("Call Ravi"), com.abhedyam.util.Txt.raw("Ask about the TV payment"), "REMINDER", r.getId(), "reminder:" + r.getId());
        assertEquals(ReminderStatus.SENT, r.getStatus());
    }
}
