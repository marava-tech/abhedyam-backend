package com.abhedyam.controller;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.SessionResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.service.OwnerSessionService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owners/{ownerId}/sessions")
@RequiredArgsConstructor
@Tag(name = "Owner Sessions", description = "Signed-in phones (More → Your devices)")
public class OwnerSessionController {

    private final OwnerSessionService service;

    @GetMapping
    @Operation(summary = "Phones signed in to this shop", description = "Most recently used first; current = the phone asking.")
    public ApiResponse<List<SessionResponse>> list(@PathVariable UUID ownerId) {
        return ApiResponse.success(service.list(self(ownerId), SecurityUtil.getCurrentSessionId()));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Log one phone out", description = "Its next request gets 401 SESSION_REVOKED and its pushes stop.")
    public ApiResponse<Void> revoke(@PathVariable UUID ownerId, @PathVariable UUID sessionId) {
        service.revoke(self(ownerId), sessionId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "Name a phone", description = "Body {\"label\": \"Ravi's phone\"}; blank clears it. Shown in Your devices instead of the phone model.")
    public ApiResponse<Void> rename(@PathVariable UUID ownerId, @PathVariable UUID sessionId, @RequestBody Map<String, String> body) {
        service.rename(self(ownerId), sessionId, body.get("label"));
        return ApiResponse.success(null);
    }

    @PostMapping("/logout-others")
    @Operation(summary = "Log out all other phones")
    public ApiResponse<Map<String, Integer>> revokeOthers(@PathVariable UUID ownerId) {
        return ApiResponse.success(Map.of("loggedOut", service.revokeOthers(self(ownerId), SecurityUtil.getCurrentSessionId())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out this phone", description = "Called by the app's Log out, so the phone leaves Your devices.")
    public ApiResponse<Void> logout(@PathVariable UUID ownerId) {
        UUID current = SecurityUtil.getCurrentSessionId();
        if (current != null) service.revoke(self(ownerId), current);
        return ApiResponse.success(null);
    }

    private static UUID self(UUID ownerId) {
        if (!ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only see your own phones");
        }
        return ownerId;
    }
}
