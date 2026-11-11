package com.abhedyam.dto;

import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "Request to manually create a payment")
public class PaymentCreateRequest {
    @NotNull(message = "Sale Item ID is required")
    @Schema(description = "UUID of the sale item", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    private UUID saleItemId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Payment amount", example = "1000.00", required = true)
    private BigDecimal amount;
    
    @NotNull(message = "Payment medium is required")
    @Schema(description = "Payment medium", example = "CASH", required = true)
    private PaymentMedium medium;
    
    @Schema(description = "Payment reference/transaction ID", example = "TXN123456789")
    private String reference;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Optional: the due this payment is for (filled first)")
    private java.util.UUID dueId;
    
    @Schema(description = "Payment status", example = "SUCCESS")
    private PaymentStatus status;

    /** Set only by the write-off endpoint (never from the app's payment form). */
    @io.swagger.v3.oas.annotations.media.Schema(hidden = true)
    private Boolean writeOff;

    @io.swagger.v3.oas.annotations.media.Schema(hidden = true)
    private java.time.Instant paidAt;
}

