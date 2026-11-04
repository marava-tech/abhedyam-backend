package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AccountDeleteRequest {
    @Schema(description = "Must be exactly DELETE", example = "DELETE")
    private String confirm;
}
