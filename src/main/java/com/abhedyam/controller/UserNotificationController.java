package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.NotificationResponse;
import com.abhedyam.model.Notification;
import com.abhedyam.service.interfaces.INotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users/{userId}/notifications")
@RequiredArgsConstructor
@Tag(name = "User Notifications", description = "User-scoped notification endpoints")
public class UserNotificationController {

    private final INotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get notifications", description = "Get notifications for a user. Supports filtering by unread status.")
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @PathVariable UUID userId,
            @Parameter(description = "Filter to show only unread notifications", example = "true")
            @RequestParam(required = false) Boolean unreadOnly) {
        List<Notification> notifications = notificationService.getNotificationsForUser(userId, unreadOnly);
        List<NotificationResponse> responses = notifications.stream()
            .map(NotificationResponse::fromEntity)
            .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread count", description = "How many unread notifications the user has (the Home bell), without the list.")
    public ApiResponse<Long> getUnreadCount(@PathVariable UUID userId) {
        return ApiResponse.success(notificationService.countUnreadForUser(userId));
    }
}
