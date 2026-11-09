package com.abhedyam.service.interfaces;

import com.abhedyam.dto.AdminOverview;
import com.abhedyam.dto.AdminReferralRow;
import com.abhedyam.dto.AdminShopDetail;
import com.abhedyam.dto.AdminShopRow;
import com.abhedyam.dto.PageResponse;

import java.util.List;
import java.util.UUID;

/** The founder's dashboard: numbers, shops, access, referral payouts. Spec: specs/admin-dashboard.md. */
public interface IAdminDashboardService {
    AdminOverview overview();

    PageResponse<AdminShopRow> shops(String q, String status, String sort, int page, int size);

    AdminShopDetail shop(UUID ownerId);

    void grantDays(UUID ownerId, int days);

    void endAccess(UUID ownerId);

    List<AdminReferralRow> referrals();

    void markReferralCashPaid(UUID referralId);
}
