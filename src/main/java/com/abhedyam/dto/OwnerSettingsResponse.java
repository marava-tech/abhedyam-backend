package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Schema(description = "Owner settings response")
public class OwnerSettingsResponse {
    @Schema(description = "Settings ID (read-only, system-generated)", example = "3595381f-d038-4d6b-8fe0-dc76ebb7dde2", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;
    
    @Schema(description = "Daily quote enabled", example = "true")
    private Boolean dailyQuoteEnabled;
    
    @Schema(description = "Dark mode enabled", example = "false")
    private Boolean isDarkModeEnabled;
    
    @Schema(description = "Villages view: group customers by village and plan village visits", example = "false")
    private Boolean villageModeEnabled;
    
    @Schema(description = "Other feature flags")
    private Map<String, Object> otherFlags;
    
    @Schema(description = "Creation timestamp (read-only, system-generated)", example = "2025-11-15T10:51:15.325Z", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant createdAt;
    
    @Schema(description = "Last update timestamp (read-only, system-generated)", example = "2025-11-15T10:51:15.325Z", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant updatedAt;
}

