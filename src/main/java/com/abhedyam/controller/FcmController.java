package com.abhedyam.controller;

import com.abhedyam.dto.FcmTokenRegisterRequest;
import com.abhedyam.dto.FcmTokenUnregisterRequest;
import com.abhedyam.model.FcmDetails;
import com.abhedyam.service.interfaces.IFcmService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fcm")
@RequiredArgsConstructor
@Tag(name = "FCM", description = "FCM token management endpoints")
public class FcmController {
    
    private final IFcmService fcmService;
    
    @PostMapping("/register")
    @Operation(summary = "Register FCM token", description = "Register a new FCM token for push notifications")
    public ResponseEntity<Map<String, Object>> registerToken(@Valid @RequestBody FcmTokenRegisterRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        FcmDetails fcmDetails = fcmService.registerToken(
            userId,
            request.getToken(),
            request.getPackageName(),
            request.getDeviceId(),
            request.getDeviceType(),
            request.getLanguage()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "FCM token registered successfully");
        response.put("fcmDetailsId", fcmDetails.getId());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/unregister")
    @Operation(summary = "Unregister FCM token", description = "Unregister an FCM token")
    public ResponseEntity<Map<String, Object>> unregisterToken(@Valid @RequestBody FcmTokenUnregisterRequest request) {
        UUID userId = SecurityUtil.getCurrentUserId();
        fcmService.unregisterToken(userId, request.getToken(), request.getPackageName());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "FCM token unregistered successfully");
        
        return ResponseEntity.ok(response);
    }
}

