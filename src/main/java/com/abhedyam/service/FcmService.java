package com.abhedyam.service;

import com.abhedyam.model.FcmDetails;
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
    public FcmDetails registerToken(UUID userId, String token, String packageName, String deviceId, String deviceType) {
        Optional<FcmDetails> existing = fcmDetailsRepository.findByUserIdAndTokenAndPackageName(userId, token, packageName);
        
        if (existing.isPresent()) {
            FcmDetails fcmDetails = existing.get();
            if (deviceId != null) fcmDetails.setDeviceId(deviceId);
            if (deviceType != null) fcmDetails.setDeviceType(deviceType);
            return fcmDetailsRepository.save(fcmDetails);
        }
        
        FcmDetails fcmDetails = new FcmDetails();
        fcmDetails.setUserId(userId);
        fcmDetails.setToken(token);
        fcmDetails.setPackageName(packageName);
        fcmDetails.setDeviceId(deviceId);
        fcmDetails.setDeviceType(deviceType);
        
        return fcmDetailsRepository.save(fcmDetails);
    }
    
    @Transactional
    public void unregisterToken(UUID userId, String token, String packageName) {
        fcmDetailsRepository.deleteByUserIdAndTokenAndPackageName(userId, token, packageName);
    }
    
    public void sendNotificationToUser(UUID userId, String title, String body, String packageName) {
        List<FcmDetails> fcmDetailsList = fcmDetailsRepository.findByUserIdAndPackageName(userId, packageName);
        
        if (fcmDetailsList.isEmpty()) {
            log.warn("No FCM tokens found for user {} with package {}", userId, packageName);
            return;
        }
        
        for (FcmDetails fcmDetails : fcmDetailsList) {
            try {
                Message message = Message.builder()
                    .setToken(fcmDetails.getToken())
                    .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                    .putData("type", "reminder")
                    .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                            .setSound("default")
                            .setChannelId("reminders")
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
            } catch (FirebaseMessagingException e) {
                log.error("Failed to send FCM notification to user {} with token {}: {}", 
                    userId, fcmDetails.getToken(), e.getMessage());
                
                String errorCode = e.getErrorCode() != null ? e.getErrorCode().toString() : null;
                String errorMessage = e.getMessage();
                if ((errorCode != null && (
                    errorCode.contains("INVALID_ARGUMENT") || 
                    errorCode.contains("UNREGISTERED"))) ||
                    (errorMessage != null && errorMessage.contains("Requested entity was not found"))) {
                    log.info("Removing invalid token for user {}: {}", userId, errorCode);
                    fcmDetailsRepository.delete(fcmDetails);
                }
            } catch (Exception e) {
                log.error("Unexpected error sending FCM notification to user {}: {}", userId, e.getMessage(), e);
            }
        }
    }
}
