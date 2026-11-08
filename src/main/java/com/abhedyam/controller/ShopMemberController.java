package com.abhedyam.controller;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.InvitePreviewResponse;
import com.abhedyam.dto.MemberInviteRequest;
import com.abhedyam.dto.MemberResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.service.ShopMemberService;
import com.abhedyam.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** More → Family & helpers (owner only; RoleGuardInterceptor refuses members). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Family & helpers", description = "Invite family members and helpers to a shop")
public class ShopMemberController {

    private final ShopMemberService service;

    @PostMapping("/api/v1/owners/{ownerId}/members")
    @Operation(summary = "Invite a person", description = "Returns a 6-letter code and the link to share on WhatsApp (valid 48 h, works once).")
    public ApiResponse<MemberResponse> invite(@PathVariable UUID ownerId, @Valid @RequestBody MemberInviteRequest request) {
        return ApiResponse.success(service.invite(self(ownerId), request));
    }

    @GetMapping("/api/v1/owners/{ownerId}/members")
    @Operation(summary = "People in this shop", description = "Pending invites and members, oldest first.")
    public ApiResponse<List<MemberResponse>> list(@PathVariable UUID ownerId) {
        return ApiResponse.success(service.list(self(ownerId)));
    }

    @PostMapping("/api/v1/owners/{ownerId}/members/{memberId}/invite")
    @Operation(summary = "Share again", description = "New code and 48 h for a person who has not joined yet.")
    public ApiResponse<MemberResponse> reinvite(@PathVariable UUID ownerId, @PathVariable UUID memberId) {
        return ApiResponse.success(service.reinvite(self(ownerId), memberId));
    }

    @PatchMapping("/api/v1/owners/{ownerId}/members/{memberId}")
    @Operation(summary = "Change role", description = "Body {\"role\": \"FAMILY\" | \"HELPER\"}.")
    public ApiResponse<MemberResponse> changeRole(@PathVariable UUID ownerId, @PathVariable UUID memberId,
                                                  @RequestBody Map<String, String> body) {
        return ApiResponse.success(service.changeRole(self(ownerId), memberId, body.get("role")));
    }

    @DeleteMapping("/api/v1/owners/{ownerId}/members/{memberId}")
    @Operation(summary = "Remove a person", description = "Their phones are logged out at once. Entries they made stay.")
    public ApiResponse<Void> remove(@PathVariable UUID ownerId, @PathVariable UUID memberId) {
        service.remove(self(ownerId), memberId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/public/invites/{code}")
    @Operation(summary = "Invite preview (no login)", description = "For the join screen / page: shop name, person's name, role, still valid?")
    public ApiResponse<InvitePreviewResponse> preview(@PathVariable String code) {
        return ApiResponse.success(service.preview(code));
    }

    private static UUID self(UUID ownerId) {
        if (!ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "This is not your shop");
        }
        return ownerId;
    }
}
