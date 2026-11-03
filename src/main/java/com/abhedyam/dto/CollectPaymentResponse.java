package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of collecting a payment from a customer")
public class CollectPaymentResponse {
    private UUID customerId;
    private BigDecimal totalCollected;
    @Schema(description = "One payment per sale item the amount was applied to, oldest first")
    private List<PaymentResponse> payments;
}
