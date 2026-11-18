package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Many customers at once, each with what they owe from before (contacts or a typed list)")
public class CustomerBatchRequest {

    @NotBlank
    @Size(max = 40)
    @Schema(description = "Made once by the app and sent again on a retry, so nothing is added twice", required = true)
    private String idempotencyKey;

    @Size(max = 60)
    @Schema(description = "Name of the old-balance sale line, in the owner's language", example = "Old balance")
    private String label;

    @NotEmpty
    @Size(max = 500)
    @Valid
    @Schema(required = true)
    private List<Row> rows;

    @Data
    public static class Row {
        @Schema(example = "Ramesh")
        private String name;
        @Schema(example = "9848012345")
        private String phone;
        @Schema(example = "Kondapur")
        private String village;
        @Schema(description = "What they owe from before; 0 or empty adds only the customer", example = "4000")
        private BigDecimal owes;
        @Schema(description = "yyyy-MM-dd, optional", example = "2026-12-01")
        private String dueDate;
    }
}
