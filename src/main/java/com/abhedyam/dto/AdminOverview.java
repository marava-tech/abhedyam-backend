package com.abhedyam.dto;

import java.util.List;
import java.util.Map;

/** Founder's "Today" numbers. Spec: specs/admin-dashboard.md. */
public record AdminOverview(
        Shops shops,
        Map<String, Long> payingByProduct,
        long foundingLeft,
        long signupsToday,
        long signups7d,
        long signups30d,
        List<Day> signupsByDay,
        long activeShops7d,
        long trialsEnding3d,
        long callsDue,
        Funnel funnel30d,
        Owed owed) {

    public record Shops(long total, long trial, long paying, long readOnly, long deleting, long closed) {}
    public record Day(String date, long count) {}
    public record Funnel(long signedUp, long addedCustomer, long madeSale, long paid) {}
    public record Owed(long partnersRupees, long referralCashRupees) {}
}
