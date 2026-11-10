package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Cancel payments the owner just recorded by mistake")
public class CancelPaymentsRequest {
    @NotEmpty(message = "paymentIds is required")
    @Size(max = 50, message = "Too many payments at once")
    @Schema(description = "Ids from the collect response (one Payment in can be several rows). All are cancelled together or none.")
    private List<UUID> paymentIds;
}
