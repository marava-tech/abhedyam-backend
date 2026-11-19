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
}
