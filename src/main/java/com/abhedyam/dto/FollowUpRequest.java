package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/** "Call again on…": on create date is required; on update only what is sent changes. */
@Data
public class FollowUpRequest {
    @Schema(example = "2026-11-14", description = "Today or later")
    private LocalDate date;

    @Schema(description = "Optional, up to 200 characters")
    private String note;

    @Schema(description = "true marks it done (it leaves the list)")
    private Boolean done;
}
