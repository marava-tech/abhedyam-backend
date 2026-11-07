package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/** On create village and visitDate are required; on update only what is sent changes. */
@Data
public class CollectionVisitRequest {
    @Schema(example = "Hanamkonda")
    private String village;

    @Schema(example = "2026-11-12")
    private LocalDate visitDate;

    @Schema(description = "Optional, up to 200 characters")
    private String note;

    @Schema(description = "true marks the visit done (it leaves the list)")
    private Boolean done;
}
