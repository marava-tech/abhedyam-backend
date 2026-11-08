package com.abhedyam.dto;

import com.abhedyam.model.enums.PaymentMedium;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "Collect a lump-sum payment from a customer; applied to the oldest unpaid items first")
public class CollectPaymentRequest {
    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(example = "1500.00")
    private BigDecimal amount;

    @Schema(description = "Defaults to CASH", example = "CASH")
    private PaymentMedium medium;

    private String reference;

    @Schema(description = "Optional: the due this payment is for (\"Got paid\" on a due); it is filled first")
    private UUID dueId;

    @Schema(description = "Random id the phone makes once per Payment in screen. A repeat (double tap, retry) within 10 minutes is not recorded again.")
    private String idempotencyKey;
}
