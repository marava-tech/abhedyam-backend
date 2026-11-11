package com.abhedyam.dto;

import com.abhedyam.model.Customer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
    private UUID id;
    private String name;
    private String phone;
    private String phoneNormalized;
    private String email;
    private String imageUrl;
    private UUID ownerId;
    private String village;
    private Instant createdAt;
    private Instant updatedAt;

    @Schema(description = "Pending amount (only when includePendingAmountDetails=true)")
    private BigDecimal pendingAmount;

    @Schema(description = "Latest sale (owesOnly / sortBy=pendingAmount lists only)")
    private Instant lastSaleAt;

    @Schema(description = "Latest successful payment (owesOnly / sortBy=pendingAmount lists only)")
    private Instant lastPaymentAt;

    @Schema(description = "Owner stopped chasing this customer (old dues). Balance unchanged.")
    private boolean chasePaused;

    @Schema(description = "Collection cycle, or null")
    private CollectionCycleDto cycle;

    public static CustomerResponse fromEntity(Customer customer, String village) {
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setName(customer.getName());
        response.setPhone(customer.getPhone());
        response.setPhoneNormalized(customer.getPhoneNormalized());
        response.setEmail(customer.getEmail());
        response.setImageUrl(customer.getImageUrl());
        response.setOwnerId(customer.getOwnerId());
        response.setVillage(village);
        response.setCreatedAt(customer.getCreatedAt());
        response.setUpdatedAt(customer.getUpdatedAt());
        response.setChasePaused(Boolean.TRUE.equals(customer.getChasePaused()));
        if (customer.getCycleKind() != null) {
            response.setCycle(new CollectionCycleDto(customer.getCycleKind(), customer.getCycleDay(), customer.getCycleAmount()));
        }
        return response;
    }
}

