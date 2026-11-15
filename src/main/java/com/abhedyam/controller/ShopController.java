package com.abhedyam.controller;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.AuthResponse;
import com.abhedyam.dto.ShopResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.service.ShopService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** More → shop switcher (owner only; RoleGuardInterceptor refuses members). */
@RestController
@RequestMapping("/api/v1/owners/{ownerId}/shops")
@RequiredArgsConstructor
@Tag(name = "Shops", description = "Business plan: more shops on one Gmail")
public class ShopController {

    private final ShopService service;

    @GetMapping
    @Operation(summary = "The owner's shops", description = "Main shop first; current = the shop this phone is in.")
    public ApiResponse<List<ShopResponse>> list(@PathVariable UUID ownerId) {
        return ApiResponse.success(service.list(self(ownerId)));
    }

    @PostMapping
    @Operation(summary = "Add a shop", description = "Body {\"businessName\"}. Business plan only (else 400 PLAN_LIMIT). "
            + "Returns a token for the new shop (isNewUser=true: run shop setup); this phone leaves the current shop.")
    public ApiResponse<AuthResponse> add(@PathVariable UUID ownerId, @RequestBody Map<String, String> body,
                                         @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                         @RequestHeader(value = "X-Device-Name", required = false) String deviceName) {
        return ApiResponse.success(service.add(self(ownerId), SecurityUtil.getCurrentSessionId(), body.get("businessName"), deviceId, deviceName));
    }

    @PostMapping("/{shopId}/switch")
    @Operation(summary = "Open another shop", description = "Returns a token for that shop. 400 PLAN_PAUSED for an extra shop while the main shop is not on Business.")
    public ApiResponse<AuthResponse> switchTo(@PathVariable UUID ownerId, @PathVariable UUID shopId,
                                              @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                              @RequestHeader(value = "X-Device-Name", required = false) String deviceName) {
        return ApiResponse.success(service.switchTo(self(ownerId), SecurityUtil.getCurrentSessionId(), shopId, deviceId, deviceName));
    }

    private static UUID self(UUID ownerId) {
        if (!ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only open your own shops");
        }
        return ownerId;
    }
}
