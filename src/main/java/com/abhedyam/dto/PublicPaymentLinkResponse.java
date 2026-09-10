package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Public payload for a payment request link")
public class PublicPaymentLinkResponse {
    private String token;
    private String businessName;
    private String customerName;
    private String productName;
    private BigDecimal amountDue;
    private String vpa;
    private String ownerPhone;
}
