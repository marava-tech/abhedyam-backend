package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.ReminderCreateRequest;
import com.abhedyam.model.Reminder;
import com.abhedyam.service.interfaces.IReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reminders")
@RequiredArgsConstructor
public class ReminderController {
    
    private final IReminderService reminderService;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Reminder> create(@Valid @RequestBody ReminderCreateRequest request) {
        return ApiResponse.success(reminderService.create(request));
    }
    
    @GetMapping("/customer/{customerId}")
    public ApiResponse<List<Reminder>> getByCustomerId(@PathVariable UUID customerId) {
        return ApiResponse.success(reminderService.getByCustomerId(customerId));
    }

}

