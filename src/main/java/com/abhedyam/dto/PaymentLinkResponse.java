package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Schema(description = "Payment link created by an owner")
public class PaymentLinkResponse {
    private String token;
    private BigDecimal amount;
    private String customerName;
    private String productName;
    private Instant expiresAt;
}
