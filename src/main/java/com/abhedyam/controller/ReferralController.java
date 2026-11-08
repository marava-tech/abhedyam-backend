package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.ApplyReferralRequest;
import com.abhedyam.dto.ReferralSummaryResponse;
import com.abhedyam.service.interfaces.IReferralService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
@Tag(name = "Referrals", description = "Invite shops: friend gets 15 days, you get a month when they pay")
public class ReferralController {

    private final IReferralService referralService;

    @GetMapping
    @Operation(summary = "My referrals", description = "My code, share link, rewards and the shops that joined with it")
    public ApiResponse<ReferralSummaryResponse> summary() {
        return ApiResponse.success(referralService.summary(SecurityUtil.getCurrentUserId()));
    }

    @PostMapping("/apply")
    @Operation(summary = "Use a friend's code", description = "Adds 15 days of access; the friend is rewarded when you pay")
    public ApiResponse<Void> apply(@Valid @RequestBody ApplyReferralRequest request) {
        referralService.apply(SecurityUtil.getCurrentUserId(), request.referralCode());
        return ApiResponse.success(null);
    }
}
