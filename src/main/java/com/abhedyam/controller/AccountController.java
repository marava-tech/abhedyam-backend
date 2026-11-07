package com.abhedyam.controller;

import com.abhedyam.dto.AccountDeleteRequest;
import com.abhedyam.dto.ApiResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.service.interfaces.IAccountExportService;
import com.abhedyam.service.interfaces.IAccountService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/owner/account")
@RequiredArgsConstructor
public class AccountController {

    private final IAccountService accountService;
    private final IAccountExportService exportService;

    @Operation(summary = "Ask to delete the signed-in owner's account. It is disabled after 48 hours unless cancelled; the data is emailed after 12.")
    @DeleteMapping
    public ApiResponse<Map<String, Object>> delete(@RequestBody AccountDeleteRequest request) {
        if (request == null || !"DELETE".equals(request.getConfirm())) {
            throw new BusinessException("CONFIRMATION_REQUIRED", "Send confirm = DELETE to delete your account");
        }
        Instant at = accountService.requestDeletion(SecurityUtil.getCurrentUserId());
        return ApiResponse.success(Map.of("deleted", false, "scheduled", true, "scheduledFor", at.toString()));
    }

    @Operation(summary = "Cancel a pending account deletion")
    @PostMapping("/deletion/cancel")
    public ApiResponse<Map<String, Boolean>> cancelDeletion() {
        accountService.cancelDeletion(SecurityUtil.getCurrentUserId());
        return ApiResponse.success(Map.of("scheduled", false));
    }

    @Operation(summary = "Is an account deletion pending, and when will it happen")
    @GetMapping("/deletion")
    public ApiResponse<Map<String, Object>> deletionStatus() {
        return ApiResponse.success(accountService.deletionScheduledFor(SecurityUtil.getCurrentUserId())
                .<Map<String, Object>>map(at -> Map.of("scheduled", true, "scheduledFor", at.toString()))
                .orElse(Map.of("scheduled", false)));
    }

    @Operation(summary = "Email a zip of customers, sales and payments CSVs to the owner (once per 7 days)")
    @PostMapping("/export-request")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestExport() {
        String masked = exportService.requestExport(SecurityUtil.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("status", "QUEUED", "email", masked)));
    }
}
