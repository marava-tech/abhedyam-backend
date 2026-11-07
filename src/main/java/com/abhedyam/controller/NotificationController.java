package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.NotificationMarkReadRequest;
import com.abhedyam.dto.NotificationResponse;
import com.abhedyam.model.Notification;
import com.abhedyam.service.interfaces.INotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification management endpoints")
public class NotificationController {
    
    private final INotificationService notificationService;
    private final com.abhedyam.service.interfaces.IFcmService fcm;
    
    @GetMapping("/{id}")
    public ApiResponse<NotificationResponse> getById(@PathVariable UUID id) {
        Notification notification = notificationService.getById(id);
        return ApiResponse.success(NotificationResponse.fromEntity(notification));
    }
    
    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markAsRead(@PathVariable UUID id) {
        Notification notification = notificationService.markAsRead(id);
        return ApiResponse.success(NotificationResponse.fromEntity(notification));
    }
    
    /** Sends a push to the caller's own phones only (no inbox row, ignores mute and quiet hours). Returns how many phones got it. */
    @PostMapping("/test")
    public ApiResponse<Integer> sendTest() {
        int sent = fcm.sendNotificationToUser(com.abhedyam.util.SecurityUtil.getCurrentUserId(), "Test notification",
            "Notifications work on this phone.", com.abhedyam.util.PackageConstants.BUSINESS_APP_PACKAGE, java.util.Map.of());
        return ApiResponse.success(sent);
    }

    @PostMapping("/mark-read")
    public ApiResponse<List<NotificationResponse>> markMultipleAsRead(@Valid @RequestBody NotificationMarkReadRequest request) {
        List<Notification> notifications = notificationService.markMultipleAsRead(request);
        List<NotificationResponse> responses = notifications.stream()
            .map(NotificationResponse::fromEntity)
            .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }
}

