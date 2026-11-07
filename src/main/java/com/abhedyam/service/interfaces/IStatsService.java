package com.abhedyam.service.interfaces;

import com.abhedyam.dto.AnalyticsRequest;
import com.abhedyam.dto.AnalyticsResponse;
import com.abhedyam.dto.DashboardStatsResponse;
import com.abhedyam.dto.RecentActivityResponse;
import com.abhedyam.dto.StatsRequest;
import com.abhedyam.dto.StatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface IStatsService {
    void aggregateDailyStats(LocalDate date);

    List<StatsResponse> getStats(StatsRequest request);

    DashboardStatsResponse getDashboardStats();

    Page<RecentActivityResponse> getRecentActivities(Pageable pageable);

    AnalyticsResponse getAnalytics(AnalyticsRequest request);
}
