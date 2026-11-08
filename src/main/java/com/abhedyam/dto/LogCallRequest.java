package com.abhedyam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogCallRequest(@NotBlank String stage, @Size(max = 500) String note) {
}
