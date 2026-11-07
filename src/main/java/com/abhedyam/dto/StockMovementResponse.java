package com.abhedyam.dto;

import com.abhedyam.model.enums.StockMovementType;
import com.abhedyam.model.enums.StockRemovalReason;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class StockMovementResponse {
    private UUID id;
    private StockMovementType type;
    private BigDecimal quantity;
    private BigDecimal stockAfter;
    private StockRemovalReason reason;
    private String note;
    private String saleTransactionId;
    private Instant createdAt;
}
