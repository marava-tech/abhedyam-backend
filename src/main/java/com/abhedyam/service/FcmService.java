package com.abhedyam.service;

import com.abhedyam.model.FcmDetails;
import com.abhedyam.model.enums.AppLanguage;
import com.abhedyam.repository.FcmDetailsRepository;
import com.abhedyam.service.interfaces.IFcmService;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService implements IFcmService {
    
    private final FcmDetailsRepository fcmDetailsRepository;
    
    @Transactional
    public FcmDetails registerToken(UUID userId, String token, String packageName, String deviceId, String deviceType, AppLanguage language) {
        Optional<FcmDetails> existing = fcmDetailsRepository.findByUserIdAndTokenAndPackageName(userId, token, packageName);
        
        if (existing.isPresent()) {
            FcmDetails fcmDetails = existing.get();
            if (deviceId != null) fcmDetails.setDeviceId(deviceId);
            if (deviceType != null) fcmDetails.setDeviceType(deviceType);
            if (language != null) fcmDetails.setLanguage(language);
            return fcmDetailsRepository.save(fcmDetails);
        }
        
        FcmDetails fcmDetails = new FcmDetails();
        fcmDetails.setUserId(userId);
        fcmDetails.setToken(token);
        fcmDetails.setPackageName(packageName);
        fcmDetails.setDeviceId(deviceId);
        fcmDetails.setDeviceType(deviceType);
        fcmDetails.setLanguage(language);
        
        return fcmDetailsRepository.save(fcmDetails);
    }
    
    @Transactional
    public void unregisterToken(UUID userId, String token, String packageName) {
        fcmDetailsRepository.deleteByUserIdAndTokenAndPackageName(userId, token, packageName);
    }
    
    @Override
    public int sendNotificationToUser(UUID userId, java.util.function.Function<String, String[]> textIn, String packageName,
                                      java.util.Map<String, String> data) {
        List<FcmDetails> fcmDetailsList = fcmDetailsRepository.findByUserIdAndPackageName(userId, packageName);
        
        if (fcmDetailsList.isEmpty()) {
            log.warn("No FCM tokens found for user {} with package {}", userId, packageName);
            return 0;
        }
        
        int sent = 0;
        for (FcmDetails fcmDetails : fcmDetailsList) {
            try {
                // The phone's app language (sent with its token); older apps sent none = English.
                String[] text = textIn.apply(fcmDetails.getLanguage() == null ? "en" : fcmDetails.getLanguage().code());
                String title = text[0], body = text[1];
                Message message = Message.builder()
                    .setToken(fcmDetails.getToken())
                    .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                    .putData("type", "reminder")
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                            .setSound("default")
                            .setChannelId(channelFor(data))
                            .build())
                        .build())
                    .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                            .setSound("default")
                            .setBadge(1)
                            .build())
                        .build())
                    .build();
                
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("FCM notification sent successfully to user {}: {}", userId, response);
                sent++;
            } catch (FirebaseMessagingException e) {
                log.error("Failed to send FCM notification to user {} with token {}: {}", 
                    userId, fcmDetails.getToken(), e.getMessage());
                
                // A phone that uninstalled or got a new token: forget it, or every push keeps trying it (seen: "NotRegistered").
                var code = e.getMessagingErrorCode();
                if (code == com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED
                    || code == com.google.firebase.messaging.MessagingErrorCode.INVALID_ARGUMENT) {
                    String errorCode = String.valueOf(code);
                    log.info("Removing invalid token for user {}: {}", userId, errorCode);
                    fcmDetailsRepository.delete(fcmDetails);
                }
            } catch (Exception e) {
                log.error("Unexpected error sending FCM notification to user {}: {}", userId, e.getMessage(), e);
            }
        }
        return sent;
    }

    /**
     * Silent "something changed" ping to the owner's other phones (data only: nothing is shown, apps before 1.13 ignore it).
     * The open app reloads its lists. [exceptDeviceId]: the phone that made the change.
     */
    public void sendSync(UUID userId, String exceptDeviceId) {
        for (FcmDetails d : fcmDetailsRepository.findByUserIdAndPackageName(userId, com.abhedyam.util.PackageConstants.BUSINESS_APP_PACKAGE)) {
            if (exceptDeviceId != null && exceptDeviceId.equals(d.getDeviceId())) continue;
            try {
                FirebaseMessaging.getInstance().send(Message.builder()
                        .setToken(d.getToken())
                        .putData("type", "sync")
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.NORMAL)
                                .setCollapseKey("sync")
                                .setTtl(60_000)
                                .build())
                        .build());
            } catch (FirebaseMessagingException e) {
                var code = e.getMessagingErrorCode();
                if (code == com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED) fcmDetailsRepository.delete(d);
                else log.warn("Sync ping failed for user {}: {}", userId, e.getMessage());
            } catch (Exception e) {
                log.warn("Sync ping failed for user {}: {}", userId, e.getMessage());
            }
        }
    }

    /** Android channel per kind, so the owner can silence one kind in phone settings. The app creates these channels;
     *  a phone on an older build falls back to the manifest default ("reminders"). */
    static String channelFor(java.util.Map<String, String> data) {
        String category = data.getOrDefault("category", "");
        String entity = data.getOrDefault("relatedEntityType", "");
        if (category.equals("PAYMENT_NEEDS_OK")) return "payments";
        if (category.equals("DUE_DIGEST")) return "summary";
        if (entity.equals("DUE") || entity.equals("VISIT")) return "dues";
        return "reminders";
    }
}
