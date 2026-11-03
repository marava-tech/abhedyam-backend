package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.service.interfaces.IReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/reminders")
@RequiredArgsConstructor
public class OwnerReminderController {

    private final IReminderService reminderService;

    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable UUID ownerId, @PathVariable UUID id) {
        reminderService.cancel(ownerId, id);
        return ApiResponse.success(null);
    }
}
