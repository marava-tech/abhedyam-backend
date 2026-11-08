package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.BillingStatusResponse;
import com.abhedyam.service.interfaces.IBillingService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Billing", description = "Owner access (trial / paid / read-only) and the RevenueCat webhook")
public class BillingController {

    private final IBillingService billingService;

    @Value("${app.billing.revenuecat-webhook-auth:}")
    private String webhookAuth;

    @GetMapping("/me")
    @Operation(summary = "My access", description = "Access end date, days left, whether paying, founding seats left")
    public ApiResponse<BillingStatusResponse> me() {
        return ApiResponse.success(billingService.status(SecurityUtil.getCurrentUserId()));
    }

    @PostMapping("/revenuecat")
    @Operation(summary = "RevenueCat webhook", description = "Google Play subscription events. Needs the Authorization header set in RevenueCat.")
    public ResponseEntity<Map<String, Object>> revenueCat(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (webhookAuth.isBlank() || authorization == null
                || !MessageDigest.isEqual(webhookAuth.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
            log.warn("RevenueCat webhook rejected (bad or missing Authorization; configured={})", !webhookAuth.isBlank());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> event = body.get("event") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        boolean applied = event != null && billingService.applyRevenueCatEvent(event);
        return ResponseEntity.ok(Map.of("applied", applied));
    }
}
