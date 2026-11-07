package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create an image store entry")
public class ImageStoreCreateRequest {

    @NotBlank
    @Schema(description = "Display name", example = "Rice bag", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Tags for search", example = "[\"rice\", \"grocery\"]")
    private List<String> tags;

    @NotBlank
    @Schema(description = "Image URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageUrl;

    @Schema(description = "Optional description")
    private String description;
}
