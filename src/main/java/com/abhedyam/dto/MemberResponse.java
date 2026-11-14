package com.abhedyam.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A family member / helper, for More → Family & helpers. [status] INVITED or ACTIVE; for INVITED, [code] + [link]
 * to share and [expired] once 48 h passed (share again makes a new code).
 */
public record MemberResponse(UUID id, String name, String role, String status, String code, String link,
                             Instant inviteExpiresAt, boolean expired, String email, Instant joinedAt, Instant lastSeenAt, boolean paused) {}
