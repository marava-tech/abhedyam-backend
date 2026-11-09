package com.abhedyam.controller;

import com.abhedyam.dto.*;
import com.abhedyam.service.interfaces.IGrowthAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Founder growth tools (admin dashboard). Admin TOTP token only, like every /api/v1/admin route (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin growth", description = "Trial call list and partners")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminGrowthController {

    private final IGrowthAdminService growth;

    @GetMapping("/trial-calls")
    @Operation(summary = "Trial shops to call", description = "Non-paying shops at day 3, day 20, ≤3 days left or ended in the last 7 days; to-call first")
    public ApiResponse<List<TrialCallRow>> trialCalls() {
        return ApiResponse.success(growth.trialCalls());
    }

    @PostMapping("/owners/{ownerId}/calls")
    @Operation(summary = "Log a founder call", description = "Marks this stage's call done, with an optional note")
    public ApiResponse<Void> logCall(@PathVariable UUID ownerId, @Valid @RequestBody LogCallRequest request) {
        growth.logCall(ownerId, request.stage(), request.note());
        return ApiResponse.success(null);
    }

    @GetMapping("/partners")
    @Operation(summary = "Partners", description = "Code, shops joined / paid, ₹ owed and paid")
    public ApiResponse<List<PartnerRow>> partners() {
        return ApiResponse.success(growth.partners());
    }

    @PostMapping("/partners")
    @Operation(summary = "Add a partner", description = "Creates the partner with a new ABH… code")
    public ApiResponse<PartnerRow> createPartner(@Valid @RequestBody CreatePartnerRequest request) {
        return ApiResponse.success(growth.createPartner(request));
    }

    @PostMapping("/partners/{partnerId}/payout")
    @Operation(summary = "Mark payout done", description = "Marks all unpaid earnings as paid (after paying by UPI); returns the ₹ total")
    public ApiResponse<Map<String, Long>> payOut(@PathVariable UUID partnerId) {
        return ApiResponse.success(Map.of("paidRupees", growth.payOut(partnerId)));
    }
}
