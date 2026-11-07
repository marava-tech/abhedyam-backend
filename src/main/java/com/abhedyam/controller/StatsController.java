package com.abhedyam.controller;

import com.abhedyam.dto.AnalyticsRequest;
import com.abhedyam.dto.AnalyticsResponse;
import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.DashboardStatsResponse;
import com.abhedyam.dto.RecentActivityResponse;
import com.abhedyam.dto.StatsRequest;
import com.abhedyam.dto.StatsResponse;
import com.abhedyam.service.interfaces.IStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Statistics and dashboard APIs")
public class StatsController {

    private final IStatsService statsService;

    @GetMapping
    @Operation(summary = "Get statistics", description = "Get daily statistics for a date range")
    public ApiResponse<List<StatsResponse>> getStats(@ModelAttribute StatsRequest request) {
        if (request.getStartDate() == null) {
            request.setStartDate(LocalDate.now().minusDays(30));
        }
        if (request.getEndDate() == null) {
            request.setEndDate(LocalDate.now());
        }
        return ApiResponse.success(statsService.getStats(request));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics", description = "Get dashboard statistics including total stock, low stock count, last week sales, and weekly growth")
    public ApiResponse<DashboardStatsResponse> getDashboardStats() {
        return ApiResponse.success(statsService.getDashboardStats());
    }

    @GetMapping("/recent-activities")
    @Operation(summary = "Get recent activities", description = "Get recent activities with pagination (default page size: 10)")
    public ApiResponse<Page<RecentActivityResponse>> getRecentActivities(
            @Parameter(description = "Page number (0-indexed)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(statsService.getRecentActivities(pageable));
    }

    @PostMapping("/analytics")
    @Operation(summary = "Get analytics data", description = "Get analytics data by type (DAILY, WEEKLY, MONTHLY) and owner ID. Daily returns last 7 days, Weekly returns weekly breakdown of last 4 weeks, Monthly returns monthly breakdown of last 6 months")
    public ApiResponse<AnalyticsResponse> getAnalytics(@Valid @RequestBody AnalyticsRequest request) {
        return ApiResponse.success(statsService.getAnalytics(request));
    }
}
