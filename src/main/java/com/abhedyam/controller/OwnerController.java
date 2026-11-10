package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.OwnerResponse;
import com.abhedyam.dto.OwnerSummaryResponse;
import com.abhedyam.dto.OwnerUpdateRequest;
import com.abhedyam.service.interfaces.IOwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners")
@RequiredArgsConstructor
@Tag(name = "Owners", description = "Owner management APIs")
public class OwnerController {
    
    private final IOwnerService ownerService;
    
    @GetMapping("/{id}")
    public ApiResponse<OwnerResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(ownerService.getById(id));
    }
    
    @PatchMapping("/{id}")
    public ApiResponse<OwnerResponse> updateOwner(@PathVariable UUID id, @Valid @RequestBody OwnerUpdateRequest request) {
        return ApiResponse.success(ownerService.updateOwnerForOwner(id, request));
    }
    
    @GetMapping("/{id}/summary")
    @Operation(summary = "Get owner summary statistics", description = "Get summary statistics for an owner including total pending amount, total customers, number of villages, and total collected amount")
    public ApiResponse<OwnerSummaryResponse> getOwnerSummary(
            @Parameter(description = "Owner ID") 
            @PathVariable UUID id) {
        return ApiResponse.success(ownerService.getOwnerSummary(id));
    }
}

