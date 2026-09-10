package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to verify a customer login OTP")
public class OtpVerifyRequest {
    @NotBlank
    @Schema(description = "Phone number", example = "+919876543210")
    private String phone;

    @NotBlank
    @Schema(description = "6-digit OTP", example = "123456")
    private String otp;
}
