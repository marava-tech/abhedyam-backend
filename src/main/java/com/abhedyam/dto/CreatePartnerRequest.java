package com.abhedyam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePartnerRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 20) String phone,
        @Size(max = 100) String upiId) {
}
