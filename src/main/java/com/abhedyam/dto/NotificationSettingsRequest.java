package com.abhedyam.dto;

import com.abhedyam.model.enums.NotificationCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/** Every field is optional; only what is sent is changed. */
@Data
public class NotificationSettingsRequest {
    @Schema(description = "Push on/off per category", example = "{\"DUE_DIGEST\": false}")
    private Map<NotificationCategory, Boolean> categories;

    @Schema(description = "Quiet hours start, HH:mm (24h). Equal start and end = no quiet hours", example = "21:00")
    private String quietStart;

    @Schema(description = "Quiet hours end, HH:mm (24h)", example = "08:00")
    private String quietEnd;

    @Schema(description = "Daily due digest time, HH:mm (24h)", example = "08:00")
    private String digestTime;

    @Schema(description = "IANA time zone", example = "Asia/Kolkata")
    private String timezone;
}
