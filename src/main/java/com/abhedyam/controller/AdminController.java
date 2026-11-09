package com.abhedyam.controller;

import com.abhedyam.dto.*;
import com.abhedyam.model.enums.FeedbackCategory;
import com.abhedyam.service.AdminService;
import com.abhedyam.service.interfaces.IAdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Founder dashboard. Admin TOTP token only (SecurityConfig). Spec: specs/admin-dashboard.md. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Founder dashboard: numbers, shops, access, referrals, feedback")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final IAdminDashboardService dashboard;
    private final AdminService adminService;

    @GetMapping("/overview")
    @Operation(summary = "Today", description = "Shops by status, paying by plan, signups, active shops, funnel, money owed")
    public ApiResponse<AdminOverview> overview() {
        return ApiResponse.success(dashboard.overview());
    }

    @GetMapping("/shops")
    @Operation(summary = "Shops", description = "Search (name, shop, email, phone), filter by status, sort newest|lastSeen|customers|daysLeft")
    public ApiResponse<PageResponse<AdminShopRow>> shops(
            @RequestParam(required = false) String q,
            @Parameter(description = "TRIAL, PAYING, READ_ONLY, DELETING or CLOSED") @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.success(dashboard.shops(q, status, sort, page, size));
    }

    @GetMapping("/shops/{ownerId}")
    @Operation(summary = "One shop", description = "Totals, setup answers, devices, family/helpers, referrals, calls, feedback")
    public ApiResponse<AdminShopDetail> shop(@PathVariable UUID ownerId) {
        return ApiResponse.success(dashboard.shop(ownerId));
    }

    @PostMapping("/shops/{ownerId}/access")
    @Operation(summary = "Give days", description = "Adds 1–3650 days of full access from the later of now and the current end")
    public ApiResponse<Void> grantAccess(@PathVariable UUID ownerId, @Valid @RequestBody GrantAccessRequest request) {
        dashboard.grantDays(ownerId, request.days());
        return ApiResponse.success(null);
    }

    @PostMapping("/shops/{ownerId}/access/end")
    @Operation(summary = "End access", description = "Access ends now; the app becomes read-only")
    public ApiResponse<Void> endAccess(@PathVariable UUID ownerId) {
        dashboard.endAccess(ownerId);
        return ApiResponse.success(null);
    }

    @GetMapping("/referrals")
    @Operation(summary = "Referrals", description = "Shop → friend shop, newest first, with ₹200 cash rewards and whether they are paid")
    public ApiResponse<List<AdminReferralRow>> referrals() {
        return ApiResponse.success(dashboard.referrals());
    }

    @PostMapping("/referrals/{referralId}/cash-paid")
    @Operation(summary = "Mark ₹200 paid", description = "After paying the referrer by UPI")
    public ApiResponse<Void> referralCashPaid(@PathVariable UUID referralId) {
        dashboard.markReferralCashPaid(referralId);
        return ApiResponse.success(null);
    }

    @GetMapping("/feedbacks")
    @Operation(summary = "Feedback", description = "Filter by user, category or text; paged")
    public ApiResponse<PageResponse<AdminFeedbackResponse>> listFeedbacks(
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "category", required = false) FeedbackCategory category,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ApiResponse.success(adminService.listFeedbacks(userId, category, search, page, size, sortBy, sortDirection));
    }
}
