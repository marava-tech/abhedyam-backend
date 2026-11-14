package com.abhedyam.dto;

import java.time.Instant;
import java.util.UUID;

/** A signed-in phone, for More → Your devices. [current] = the phone asking; [person] = member name, null = owner.
 *  [deviceName] = the owner's name for it when set, else the phone model; [model] = always the phone model. */
public record SessionResponse(UUID id, String deviceName, Instant signedInAt, Instant lastSeenAt, boolean current, String person,
                              String label, String model) {}
