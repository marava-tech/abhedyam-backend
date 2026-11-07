package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Request to update owner settings")
public class OwnerSettingsUpdateRequest {
    
    @Schema(description = "Dark mode enabled", example = "false")
    private Boolean isDarkModeEnabled;
    
    @Schema(description = "Villages view: group customers by village and plan village visits", example = "false")
    private Boolean villageModeEnabled;
    
    @Schema(description = "Location tools: map pins, directions, nearby customers, visit route", example = "true")
    private Boolean locationToolsEnabled;
    
    @Schema(description = "Other feature flags")
    private Map<String, Object> otherFlags;
}

