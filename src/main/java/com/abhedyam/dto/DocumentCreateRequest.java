package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to create a document")
public class DocumentCreateRequest {
    @NotBlank(message = "Document name is required")
    @Schema(description = "Document name", example = "Terms and Conditions", required = true)
    private String name;
    
    @NotBlank(message = "MIME type is required")
    @Schema(description = "MIME type of the document", example = "application/pdf", required = true)
    private String mimeType;
    
    @NotBlank(message = "Upload URL is required")
    @Schema(description = "URL of the uploaded document", example = "https://res.cloudinary.com/...", required = true)
    private String uploadedUrl;
}

