package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to apply a referral code")
public class ApplyReferralRequest {

    @NotBlank(message = "Referral code cannot be blank")
    @Schema(description = "The referral code of the inviter", example = "ABH8X29K")
    private String referralCode;

    @Schema(description = "Hardware device identifier for fraud prevention", example = "c89f1a23e5")
    private String deviceHardwareId;
}
