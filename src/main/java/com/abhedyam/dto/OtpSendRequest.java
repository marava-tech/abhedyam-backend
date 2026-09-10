package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to send a customer login OTP")
public class OtpSendRequest {
    @NotBlank
    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;
}
