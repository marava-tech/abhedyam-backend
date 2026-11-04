package com.abhedyam.service;

import com.abhedyam.model.Reminder;
import com.abhedyam.model.enums.NotificationCategory;
import com.abhedyam.model.enums.NotificationType;
import com.abhedyam.model.enums.ReminderStatus;
import com.abhedyam.repository.ReminderRepository;
import com.abhedyam.service.interfaces.IReminderSchedulerService;
import com.abhedyam.util.PackageConstants;
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
    private final NotificationService notificationService;
    private final FcmService fcmService;
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

        // The owner's own reminder: the dispatcher handles the in-app row, mute, quiet hours and push.
        if (reminder.getCustomerId() == null) {
            dispatcher.notify(ownerId, NotificationCategory.OWNER_REMINDER, NotificationType.INFO,
                reminder.getName(), reminder.getText(), "REMINDER", reminder.getId(), "reminder:" + reminder.getId());
            return;
        }

        // A reminder addressed to a customer (customer app): not part of the owner notification engine.
        UUID customerId = reminder.getCustomerId();
        com.abhedyam.model.Notification notification = new com.abhedyam.model.Notification();
        notification.setOwnerId(ownerId);
        notification.setUserId(customerId);
        notification.setType(NotificationType.INFO);
        notification.setMessage(reminder.getText());
        notification.setTimestamp(Instant.now());
        notification.setIsRead(false);
        notification.setRelatedEntityId(reminder.getId());
        notification.setRelatedEntityType("REMINDER");
        notification.setRetryCount(0);
        notificationService.create(notification);

        List<String> packages = reminder.getPackages();
        if (packages == null || packages.isEmpty()) {
            packages = List.of(PackageConstants.CUSTOMER_APP_PACKAGE);
        }
        for (String packageName : packages) {
            try {
                fcmService.sendNotificationToUser(customerId, reminder.getName(), reminder.getText(), packageName);
                log.info("FCM notification sent for reminder {} to customer {} with package {}", reminder.getId(), customerId, packageName);
            } catch (Exception e) {
                log.error("Failed to send FCM notification for reminder {} to customer {} with package {}: {}",
                    reminder.getId(), customerId, packageName, e.getMessage());
            }
        }
    }
}
