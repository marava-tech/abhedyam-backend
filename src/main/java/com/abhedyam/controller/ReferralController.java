package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.ApplyReferralRequest;
import com.abhedyam.dto.ReferralSummaryResponse;
import com.abhedyam.service.ReferralService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Referrals", description = "Referral program and viral growth APIs")
@SecurityRequirement(name = "bearerAuth")
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/summary")
    @Operation(summary = "Get referral summary", description = "Get referral code, share link, and list of referred owners with progress")
    public ResponseEntity<ApiResponse<ReferralSummaryResponse>> getReferralSummary() {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        ReferralSummaryResponse summary = referralService.getReferralSummary(ownerId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @PostMapping("/apply")
    @Operation(summary = "Apply a referral code", description = "Apply a friend's referral code to receive welcome PRO bonus")
    public ResponseEntity<ApiResponse<String>> applyReferralCode(@Valid @RequestBody ApplyReferralRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        referralService.applyReferralCode(ownerId, request);
        return ResponseEntity.ok(ApiResponse.success("Referral code applied successfully! 30-day welcome PRO boost activated."));
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Trigger referral evaluation", description = "Evaluate pending referrals immediately")
    public ResponseEntity<ApiResponse<String>> evaluateReferrals() {
        int evaluated = referralService.evaluateAllPendingReferrals();
        return ResponseEntity.ok(ApiResponse.success("Evaluated " + evaluated + " pending referrals."));
    }
}
