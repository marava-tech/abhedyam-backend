package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import com.abhedyam.model.enums.AppLanguage;
import lombok.Data;

@Data
@Schema(description = "Request to register FCM token")
public class FcmTokenRegisterRequest {
    @NotBlank(message = "FCM token is required")
    @Schema(description = "FCM registration token", example = "fcm_token_here", required = true)
    private String token;
    
    @NotBlank(message = "Package name is required")
    @Schema(description = "Package name", example = "tech.marava.abhedyamc", required = true)
    private String packageName;
    
    @Schema(description = "Device ID", example = "device_123")
    private String deviceId;
    
    @Schema(description = "Device type", example = "android")
    private String deviceType;

    @Schema(description = "App language on this phone; pushes to it are written in it (default en)", example = "te")
    private AppLanguage language;
}

