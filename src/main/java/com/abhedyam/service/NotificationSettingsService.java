package com.abhedyam.service;

import com.abhedyam.dto.NotificationSettingsRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.repository.NotificationSettingsRepository;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService implements INotificationSettingsService {

    private final NotificationSettingsRepository repository;

    @Override
    public NotificationSettings forOwner(UUID ownerId) {
        return repository.findByOwnerId(ownerId).orElseGet(() -> {
            NotificationSettings defaults = new NotificationSettings();
            defaults.setOwnerId(ownerId);
            return defaults;
        });
    }

    @Override
    public NotificationSettings get(UUID ownerId) {
        requireSelf(ownerId);
        return forOwner(ownerId);
    }

    @Override
    @Transactional
    public NotificationSettings update(UUID ownerId, NotificationSettingsRequest r) {
        requireSelf(ownerId);
        NotificationSettings s = forOwner(ownerId);
        if (r.getQuietStart() != null) s.setQuietStart(time(r.getQuietStart()));
        if (r.getQuietEnd() != null) s.setQuietEnd(time(r.getQuietEnd()));
        if (r.getDigestTime() != null) s.setDigestTime(time(r.getDigestTime()));
        if (r.getTimezone() != null) s.setTimezone(zone(r.getTimezone()));
        if (r.getCategories() != null) {
            r.getCategories().forEach((category, pushOn) -> {
                if (Boolean.FALSE.equals(pushOn)) s.getMutedCategories().add(category);
                else s.getMutedCategories().remove(category);
            });
        }
        return repository.save(s);
    }

    private void requireSelf(UUID ownerId) {
        if (!ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException("UNAUTHORIZED", "You can only access your own notification settings");
        }
    }

    static LocalTime time(String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException("INVALID_NOTIFICATION_SETTINGS", "Time must look like 08:30");
        }
    }

    static String zone(String value) {
        try {
            return ZoneId.of(value).getId();
        } catch (Exception e) {
            throw new BusinessException("INVALID_NOTIFICATION_SETTINGS", "Unknown time zone: " + value);
        }
    }
}
