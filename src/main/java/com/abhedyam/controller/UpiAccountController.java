package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.UpiAccountCreateRequest;
import com.abhedyam.dto.UpiAccountResponse;
import com.abhedyam.service.interfaces.IUpiAccountManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upi-accounts")
@RequiredArgsConstructor
public class UpiAccountController {
    
    private final IUpiAccountManagementService upiAccountManagementService;
    
    @GetMapping("/owner/{ownerId}")
    public ApiResponse<UpiAccountResponse> getUpiAccountByOwnerId(@PathVariable UUID ownerId) {
        return ApiResponse.success(upiAccountManagementService.getUpiAccountByOwnerId(ownerId));
    }
    
    @PatchMapping("/owner/{ownerId}")
    public ApiResponse<UpiAccountResponse> updateOwnerUpiAccount(
            @PathVariable UUID ownerId,
            @Valid @RequestBody UpiAccountCreateRequest request) {
        return ApiResponse.success(upiAccountManagementService.updateUpiAccountForOwner(ownerId, request));
    }

    @PostMapping("/owner/{ownerId}/verify")
    public ApiResponse<UpiAccountResponse> verifyOwnerVpa(@PathVariable UUID ownerId) {
        return ApiResponse.success(upiAccountManagementService.verifyVpaForOwner(ownerId));
    }
}

