package com.abhedyam.service;

import com.abhedyam.model.Reminder;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.model.enums.ReminderStatus;
import com.abhedyam.repository.ReminderRepository;
import com.abhedyam.service.interfaces.IReminderSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderSchedulerService implements IReminderSchedulerService {
    
    private final ReminderRepository reminderRepository;
    private final NotificationDispatcher dispatcher;
    
    @Scheduled(fixedRate = 60000)
    @Override
    @Transactional
    public void processDueReminders() {
        Instant now = Instant.now();
        Instant maxTime = now.plus(1, ChronoUnit.MINUTES);
        
        List<Reminder> dueReminders = reminderRepository.findDueReminders(
            ReminderStatus.PENDING, 
            maxTime
        );
        
        for (Reminder reminder : dueReminders) {
            try {
                reminder.setStatus(ReminderStatus.SENT);
                reminderRepository.save(reminder);
                sendReminder(reminder);
                log.info("Reminder sent and saved: {}", reminder.getId());
            } catch (Exception e) {
                log.error("Error sending reminder: {}", reminder.getId(), e);
                reminder.setStatus(ReminderStatus.PENDING);
                reminderRepository.save(reminder);
            }
        }
    }
    
    private void sendReminder(Reminder reminder) {
        UUID ownerId = reminder.getOwnerId();
        if (ownerId == null) {
            log.warn("Cannot create notification for reminder {}: ownerId is null", reminder.getId());
            return;
        }

        // Every reminder is the owner's own, with or without a customer attached (there is no customer app).
        // The dispatcher handles the in-app row, mute, quiet hours and push.
        dispatcher.notify(ownerId, NotificationCategory.OWNER_REMINDER, NotificationType.INFO,
            com.abhedyam.util.Txt.raw(reminder.getName()), com.abhedyam.util.Txt.raw(reminder.getText()), // the owner's own words
            "REMINDER", reminder.getId(), "reminder:" + reminder.getId());
    }
}
