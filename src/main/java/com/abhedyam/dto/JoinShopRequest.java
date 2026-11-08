package com.abhedyam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinShopRequest {
    @NotBlank(message = "Google sign-in is required")
    private String idToken;

    @NotBlank(message = "Invite code is required")
    private String code;
}
