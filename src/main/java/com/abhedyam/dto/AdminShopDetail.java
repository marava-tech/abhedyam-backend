package com.abhedyam.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Everything the founder sees about one shop. Spec: specs/admin-dashboard.md. */
public record AdminShopDetail(
        AdminShopRow shop,
        long customers,
        long sales,
        long payments,
        long collected30dRupees,
        Map<String, Object> setup,
        Boolean villageMode,
        Boolean locationTools,
        Instant deletionRequestedAt,
        List<Device> devices,
        List<Member> members,
        List<Friend> referredFriends,
        List<Call> calls,
        List<FeedbackItem> feedback) {

    public record Device(String name, String memberName, Instant lastSeenAt) {}
    public record Member(String name, String email, String role, String status, Instant joinedAt) {}
    public record Friend(java.util.UUID id, String name, Instant joinedAt, boolean paid) {}
    public record Call(String stage, String note, Instant createdAt) {}
    public record FeedbackItem(String category, String text, String imageUrl, Instant createdAt) {}
}
