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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/owner/account")
@RequiredArgsConstructor
public class AccountController {

    private final IAccountService accountService;
    private final IAccountExportService exportService;

    @Operation(summary = "Permanently delete the signed-in owner's account and all their data")
    @DeleteMapping
    public ApiResponse<Map<String, Boolean>> delete(@RequestBody AccountDeleteRequest request) {
        if (request == null || !"DELETE".equals(request.getConfirm())) {
            throw new BusinessException("CONFIRMATION_REQUIRED", "Send confirm = DELETE to delete your account");
        }
        accountService.deleteAccount(SecurityUtil.getCurrentUserId());
        return ApiResponse.success(Map.of("deleted", true));
    }

    @Operation(summary = "Email a zip of customers, sales and payments CSVs to the owner (once per 7 days)")
    @PostMapping("/export-request")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestExport() {
        String masked = exportService.requestExport(SecurityUtil.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(Map.of("status", "QUEUED", "email", masked)));
    }
}
