package com.abhedyam.service.interfaces;

import com.abhedyam.model.FcmDetails;

import java.util.UUID;

public interface IFcmService {
    FcmDetails registerToken(UUID userId, String token, String packageName, String deviceId, String deviceType);
    void unregisterToken(UUID userId, String token, String packageName);
    void sendNotificationToUser(UUID userId, String title, String body, String packageName);
}

