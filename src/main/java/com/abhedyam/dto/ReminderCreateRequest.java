package com.abhedyam.dto;

import com.abhedyam.model.enums.ReminderChannel;
import com.abhedyam.model.enums.ReminderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Request to create a reminder")
public class ReminderCreateRequest {
    @Schema(description = "UUID of the customer (optional)", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID customerId;
    
    @NotBlank(message = "Reminder name is required")
    @Schema(description = "Reminder name", example = "Follow up call", required = true)
    private String name;
    
    @NotNull(message = "Reminder type is required")
    @Schema(description = "Type of reminder", example = "FOLLOW_UP", required = true)
    private ReminderType type;
    
    @Schema(description = "Delivery channel. Only IN_APP is supported (optional, kept for older app versions)", example = "IN_APP")
    private ReminderChannel channel;
    
    @NotNull(message = "Due time is required")
    @Schema(description = "Due date and time", example = "2024-12-31T10:00:00Z", required = true)
    private Instant dueAt;
    
    @NotBlank(message = "Reminder text is required")
    @Schema(description = "Reminder message text", example = "Call customer about payment", required = true)
    private String text;
    
    @Schema(description = "Package names for targeting notifications", example = "[\"tech.marava.abhedyam\", \"tech.marava.abhedyamc\"]")
    private java.util.List<String> packages;
}
