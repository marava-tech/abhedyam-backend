package com.abhedyam.dto;

import com.abhedyam.model.enums.ReferralStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Details and qualification progress of a referred shop owner")
public class ReferredOwnerProgressDto {

    private UUID referralId;
    private UUID refereeOwnerId;
    private String businessName;
    private ReferralStatus status;
    private Instant createdAt;

    // Progress metrics
    private int customerCount;
    private int requiredCustomers; // 25

    private int activeDaysCount;
    private int requiredActiveDays; // 3

    private int transactionCount;
    private int requiredTransactions; // 15

    private BigDecimal totalVolume;
    private BigDecimal requiredVolume; // 3000

    private int billsSharedCount;
    private int requiredBillsShared; // 2

    private int progressPercentage; // 0 - 100
    private Instant rewardedAt;
}
