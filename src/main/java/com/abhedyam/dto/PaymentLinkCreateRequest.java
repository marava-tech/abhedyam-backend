package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "Create a shareable payment request link")
public class PaymentLinkCreateRequest {
    @NotNull
    private UUID customerId;
    private UUID saleItemId;
    @NotNull
    @Positive
    private BigDecimal amount;
}
