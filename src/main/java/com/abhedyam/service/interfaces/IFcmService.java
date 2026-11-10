package com.abhedyam.service.interfaces;

import com.abhedyam.model.FcmDetails;
import com.abhedyam.model.enums.AppLanguage;

import java.util.UUID;

public interface IFcmService {
    FcmDetails registerToken(UUID userId, String token, String packageName, String deviceId, String deviceType, AppLanguage language);
    void unregisterToken(UUID userId, String token, String packageName);
    default int sendNotificationToUser(UUID userId, String title, String body, String packageName) {
        return sendNotificationToUser(userId, title, body, packageName, java.util.Map.of());
    }

    /** {@code data} is delivered with the push so the app can open the right screen on tap (values are strings).
     *  Returns how many of the user's phones accepted it. */
    default int sendNotificationToUser(UUID userId, String title, String body, String packageName, java.util.Map<String, String> data) {
        return sendNotificationToUser(userId, lang -> new String[] {title, body}, packageName, data);
    }

    /** Each phone gets {title, body} in its own app language: {@code textIn} takes a language code ("en", "te"). */
    int sendNotificationToUser(UUID userId, java.util.function.Function<String, String[]> textIn, String packageName,
                               java.util.Map<String, String> data);
}

