package com.abhedyam.dto;

import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.enums.NotificationCategory;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.EnumMap;
import java.util.Map;

@Data
@AllArgsConstructor
public class NotificationSettingsResponse {
    private Map<NotificationCategory, Boolean> categories;
    private String quietStart;
    private String quietEnd;
    private String digestTime;
    private String timezone;

    public static NotificationSettingsResponse from(NotificationSettings s) {
        Map<NotificationCategory, Boolean> c = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory cat : NotificationCategory.values()) c.put(cat, !s.getMutedCategories().contains(cat));
        return new NotificationSettingsResponse(c, s.getQuietStart().toString(), s.getQuietEnd().toString(),
                s.getDigestTime().toString(), s.getTimezone());
    }
}
