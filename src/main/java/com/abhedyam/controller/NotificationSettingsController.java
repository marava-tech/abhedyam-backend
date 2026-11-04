package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.NotificationSettingsRequest;
import com.abhedyam.dto.NotificationSettingsResponse;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/notification-settings")
@RequiredArgsConstructor
@Tag(name = "Notification Settings", description = "Push categories, quiet hours and digest time for an owner")
public class NotificationSettingsController {

    private final INotificationSettingsService service;

    @GetMapping
    @Operation(summary = "Get notification settings", description = "Defaults apply until the owner changes something")
    public ApiResponse<NotificationSettingsResponse> get(@PathVariable UUID ownerId) {
        return ApiResponse.success(NotificationSettingsResponse.from(service.get(ownerId)));
    }

    @PutMapping
    @Operation(summary = "Update notification settings", description = "Only the fields sent are changed")
    public ApiResponse<NotificationSettingsResponse> update(@PathVariable UUID ownerId, @RequestBody NotificationSettingsRequest request) {
        return ApiResponse.success(NotificationSettingsResponse.from(service.update(ownerId, request)));
    }
}
