package com.abhedyam.service.interfaces;

import com.abhedyam.dto.NotificationSettingsRequest;
import com.abhedyam.model.NotificationSettings;

import java.util.UUID;

public interface INotificationSettingsService {
    /** Saved settings, or unsaved defaults. No access check: for server-side callers only. */
    NotificationSettings forOwner(UUID ownerId);

    /** Current user's own settings. */
    NotificationSettings get(UUID ownerId);

    NotificationSettings update(UUID ownerId, NotificationSettingsRequest request);
}
