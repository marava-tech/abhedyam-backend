package com.abhedyam.controller;

import com.abhedyam.dto.AdminFeedbackResponse;
import com.abhedyam.dto.AdminOwnerDetailResponse;
import com.abhedyam.dto.AdminOwnerListResponse;
import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.PageResponse;
import com.abhedyam.model.enums.FeedbackCategory;
import com.abhedyam.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin APIs for tracking progress")
public class AdminController {
    
    private final AdminService adminService;
    
    @GetMapping("/owners")
    @Operation(summary = "List owners", description = "Get list of owners with filters, pagination and sorting. Requires the dashboard admin token.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<PageResponse<AdminOwnerListResponse>> listOwners(
            @Parameter(description = "Filter by username (partial match)") 
            @RequestParam(value = "username", required = false) String username,
            @Parameter(description = "Filter by email (partial match)") 
            @RequestParam(value = "email", required = false) String email,
            @Parameter(description = "Page number (0-indexed)", example = "0") 
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size", example = "20") 
            @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "Sort field", example = "createdAt") 
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction", example = "DESC") 
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ApiResponse.success(adminService.listOwners(username, email, page, size, sortBy, sortDirection));
    }
    
    @GetMapping("/owners/{ownerId}")
    @Operation(summary = "Get owner details", description = "Get detailed information about an owner including sales, payments and feedbacks. Requires the dashboard admin token.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<AdminOwnerDetailResponse> getOwnerDetails(
            @Parameter(description = "Owner ID") 
            @PathVariable UUID ownerId,
            @Parameter(description = "Start date for sales/payments filter (ISO format)", example = "2025-01-01T00:00:00Z") 
            @RequestParam(value = "startDate", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @Parameter(description = "End date for sales/payments filter (ISO format)", example = "2025-01-31T23:59:59Z") 
            @RequestParam(value = "endDate", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ApiResponse.success(adminService.getOwnerDetails(ownerId, startDate, endDate));
    }
    
    @PostMapping("/owners/{ownerId}/subscription/upgrade")
    @Operation(summary = "Upgrade owner subscription", description = "Gives the owner 365 more days of full access. Requires the dashboard admin token.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> upgradeSubscription(
            @Parameter(description = "Owner ID") 
            @PathVariable UUID ownerId) {
        adminService.upgradeSubscription(ownerId);
        return ApiResponse.success(null);
    }
    
    @PostMapping("/owners/{ownerId}/subscription/downgrade")
    @Operation(summary = "Downgrade owner subscription", description = "Ends the owner's full access now (app becomes read-only). Requires the dashboard admin token.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> downgradeSubscription(
            @Parameter(description = "Owner ID") 
            @PathVariable UUID ownerId) {
        adminService.downgradeSubscription(ownerId);
        return ApiResponse.success(null);
    }
    
    @GetMapping("/feedbacks")
    @Operation(summary = "List feedbacks", description = "Get list of feedbacks with filters, pagination and sorting. Requires the dashboard admin token.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<PageResponse<AdminFeedbackResponse>> listFeedbacks(
            @Parameter(description = "Filter by user ID") 
            @RequestParam(value = "userId", required = false) UUID userId,
            @Parameter(description = "Filter by category", example = "BUG") 
            @RequestParam(value = "category", required = false) FeedbackCategory category,
            @Parameter(description = "Search in description (partial match)") 
            @RequestParam(value = "search", required = false) String search,
            @Parameter(description = "Page number (0-indexed)", example = "0") 
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size", example = "20") 
            @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "Sort field", example = "createdAt") 
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction", example = "DESC") 
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ApiResponse.success(adminService.listFeedbacks(userId, category, search, page, size, sortBy, sortDirection));
    }
}

