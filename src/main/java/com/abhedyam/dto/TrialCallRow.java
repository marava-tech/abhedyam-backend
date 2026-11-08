package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A trial shop the founder should phone now")
public record TrialCallRow(
        UUID ownerId,
        String name,
        String businessName,
        String phone,
        String email,
        Instant signedUpAt,
        @Schema(description = "1 = the day they signed up") long trialDay,
        @Schema(description = "Days of access left (0 = read-only)") long daysLeft,
        @Schema(description = "DAY_3, DAY_20, ENDING (≤3 days left) or ENDED (within 7 days)") String stage,
        long customers,
        long sales,
        @Schema(description = "When this stage's call was logged; null = still to call") Instant calledAt,
        @Schema(description = "Latest note on any call to this shop") String lastNote) {
}
