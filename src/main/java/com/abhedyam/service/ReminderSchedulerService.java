package com.abhedyam.service;

import com.abhedyam.model.Reminder;
import com.abhedyam.model.enums.ReminderChannel;
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
        if (reminder.getChannel() == ReminderChannel.IN_APP) {
            UUID ownerId = reminder.getOwnerId();
            UUID userId = reminder.getCustomerId() != null ? reminder.getCustomerId() : ownerId;
            
            if (ownerId == null) {
                log.warn("Cannot create notification for reminder {}: ownerId is null", reminder.getId());
                return;
            }
            
            if (userId == null) {
                log.warn("Cannot create notification for reminder {}: both customerId and ownerId are null for userId", reminder.getId());
                return;
            }
            
            try {
                com.abhedyam.model.Notification notification = new com.abhedyam.model.Notification();
                notification.setOwnerId(ownerId);
                notification.setUserId(userId);
                notification.setType(com.abhedyam.model.enums.NotificationType.INFO);
                notification.setMessage(reminder.getText());
                notification.setTimestamp(Instant.now());
                notification.setIsRead(false);
                notification.setRelatedEntityId(reminder.getId());
                notification.setRelatedEntityType("REMINDER");
                notification.setRetryCount(0);
                
                if (notification.getOwnerId() == null || notification.getUserId() == null) {
                    log.error("Cannot create notification for reminder {}: ownerId={}, userId={}", 
                        reminder.getId(), notification.getOwnerId(), notification.getUserId());
                    return;
                }
                
                notificationService.create(notification);
                log.debug("Notification created for reminder {}: ownerId={}, userId={}", 
                    reminder.getId(), ownerId, userId);
            } catch (Exception e) {
                log.error("Failed to create notification for reminder {}: {}", reminder.getId(), e.getMessage(), e);
                throw e;
            }
            
            List<String> packages = reminder.getPackages();
            if (packages == null || packages.isEmpty()) {
                if (reminder.getCustomerId() != null) {
                    packages = List.of(PackageConstants.CUSTOMER_APP_PACKAGE);
                } else if (reminder.getOwnerId() != null) {
                    packages = List.of(PackageConstants.BUSINESS_APP_PACKAGE);
                } else {
                    packages = PackageConstants.ALL_PACKAGES;
                }
            }
            
            String title = reminder.getName();
            String body = reminder.getText();
            
            if (reminder.getCustomerId() != null) {
                for (String packageName : packages) {
                    try {
                        fcmService.sendNotificationToUser(
                            reminder.getCustomerId(),
                            title,
                            body,
                            packageName
                        );
                        log.info("FCM notification sent for reminder {} to customer {} with package {}", 
                            reminder.getId(), reminder.getCustomerId(), packageName);
                    } catch (Exception e) {
                        log.error("Failed to send FCM notification for reminder {} to customer {} with package {}: {}", 
                            reminder.getId(), reminder.getCustomerId(), packageName, e.getMessage());
                    }
                }
            } else if (reminder.getOwnerId() != null) {
                for (String packageName : packages) {
                    try {
                        fcmService.sendNotificationToUser(
                            reminder.getOwnerId(),
                            title,
                            body,
                            packageName
                        );
                        log.info("FCM notification sent for reminder {} to owner {} with package {}", 
                            reminder.getId(), reminder.getOwnerId(), packageName);
                    } catch (Exception e) {
                        log.error("Failed to send FCM notification for reminder {} to owner {} with package {}: {}", 
                            reminder.getId(), reminder.getOwnerId(), packageName, e.getMessage());
                    }
                }
            }
        } else {
            log.debug("Skipping reminder send for non-IN_APP channel: {} (reminder: {})", reminder.getChannel(), reminder.getId());
        }
    }
}
