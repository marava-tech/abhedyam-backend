package com.abhedyam.service.interfaces;

import com.abhedyam.dto.AnalyticsRequest;
import com.abhedyam.dto.AnalyticsResponse;
import com.abhedyam.dto.DashboardStatsResponse;
import com.abhedyam.dto.RecentActivityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface IStatsService {
    DashboardStatsResponse getDashboardStats();

    Page<RecentActivityResponse> getRecentActivities(Pageable pageable);

    AnalyticsResponse getAnalytics(AnalyticsRequest request);

    /** Reports for the signed-in owner's shop, from the first day to the last (both whole days, shop time zone). */
    com.abhedyam.dto.ReportResponse report(java.time.LocalDate from, java.time.LocalDate to);
}
