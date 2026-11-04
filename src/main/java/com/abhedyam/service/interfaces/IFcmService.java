package com.abhedyam.service.interfaces;

import com.abhedyam.model.FcmDetails;

import java.util.UUID;

public interface IFcmService {
    FcmDetails registerToken(UUID userId, String token, String packageName, String deviceId, String deviceType);
    void unregisterToken(UUID userId, String token, String packageName);
    default void sendNotificationToUser(UUID userId, String title, String body, String packageName) {
        sendNotificationToUser(userId, title, body, packageName, java.util.Map.of());
    }

    /** {@code data} is delivered with the push so the app can open the right screen on tap (values are strings). */
    void sendNotificationToUser(UUID userId, String title, String body, String packageName, java.util.Map<String, String> data);
}

