package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.AnalyticsRequest;
import com.abhedyam.dto.AnalyticsResponse;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.enums.AnalyticsType;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Sales reports count today's sales at once (any sale, paid or udhar), by the owner's day. */
@ExtendWith(MockitoExtension.class)
class SalesReportTest {
    @Mock SaleItemRepository saleItems;
    @Mock INotificationSettingsService notificationSettings;
    @InjectMocks StatsService service;

    final UUID owner = UUID.randomUUID();
    final ZoneId ist = ZoneId.of("Asia/Kolkata");

    @Test
    void todayIsReadFromTheSalesThemselves() {
        when(notificationSettings.forOwner(owner)).thenReturn(new NotificationSettings()); // Asia/Kolkata
        LocalDate today = LocalDate.now(ist);
        Instant todayStart = today.atStartOfDay(ist).toInstant();
        when(saleItems.periodTotals(eq(owner), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {BigDecimal.ZERO, 0L, 0L, BigDecimal.ZERO}));
        when(saleItems.periodTotals(owner, todayStart, today.plusDays(1).atStartOfDay(ist).toInstant()))
                .thenReturn(List.<Object[]>of(new Object[] {new BigDecimal("3600"), 1L, 1L, BigDecimal.ONE}));

        AnalyticsRequest r = new AnalyticsRequest();
        r.setOwnerId(owner);
        r.setType(AnalyticsType.DAILY);
        AnalyticsResponse res = service.getAnalytics(r);

        assertEquals(7, res.getPeriods().size());
        AnalyticsResponse.PeriodAnalytics first = res.getPeriods().get(0); // newest first
        assertEquals(today, first.getPeriodStart());
        assertEquals(new BigDecimal("3600"), first.getTotalSales());
        assertEquals(1, first.getTotalOrders());
    }
}
