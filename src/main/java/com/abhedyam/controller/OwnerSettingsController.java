package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.OwnerSettingsResponse;
import com.abhedyam.dto.OwnerSettingsUpdateRequest;
import com.abhedyam.service.interfaces.IOwnerSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/settings")
@RequiredArgsConstructor
public class OwnerSettingsController {
    
    private final IOwnerSettingsService ownerSettingsService;
    
    @GetMapping
    public ApiResponse<OwnerSettingsResponse> getOwnerSettings(@PathVariable UUID ownerId) {
        return ApiResponse.success(ownerSettingsService.getOwnerSettings(ownerId));
    }
    
    /** Who to chase: a customer was called / WhatsApp'd today. Any person of the shop; merged on the server. */
    @PostMapping("/chase-contacted")
    public ApiResponse<OwnerSettingsResponse> chaseContacted(@PathVariable UUID ownerId, @RequestBody java.util.Map<String, String> body) {
        return ApiResponse.success(ownerSettingsService.addChaseContacted(ownerId, body.get("customerId")));
    }

    @PatchMapping
    public ApiResponse<OwnerSettingsResponse> updateOwnerSettings(@PathVariable UUID ownerId, @RequestBody OwnerSettingsUpdateRequest request) {
        return ApiResponse.success(ownerSettingsService.updateOwnerSettings(ownerId, request));
    }
}

