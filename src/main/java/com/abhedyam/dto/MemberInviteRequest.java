package com.abhedyam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MemberInviteRequest {
    @NotBlank(message = "Enter their name")
    @Size(max = 60, message = "Name is too long")
    private String name;

    /** FAMILY or HELPER */
    @NotNull(message = "Pick Family or Helper")
    private String role;
}
