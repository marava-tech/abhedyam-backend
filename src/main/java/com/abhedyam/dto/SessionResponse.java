package com.abhedyam.dto;

import java.time.Instant;
import java.util.UUID;

/** A signed-in phone, for More → Your devices. [current] = the phone asking. */
public record SessionResponse(UUID id, String deviceName, Instant signedInAt, Instant lastSeenAt, boolean current) {}
