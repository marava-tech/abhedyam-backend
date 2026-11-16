package com.abhedyam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "One of the owner's shops (shop switcher)")
public record ShopResponse(
        @Schema(description = "Shop id (= its owner id in every /owners/{id} call)") UUID id,
        @Schema(description = "Shop name") String name,
        @Schema(description = "The main shop: the Gmail signs in here and the plan is bought here") boolean main,
        @Schema(description = "The shop this phone is in now") boolean current,
        @Schema(description = "Can't be opened until the main shop is on Business again (nothing deleted)") boolean paused,
        @Schema(description = "Left to collect from this shop's customers (Home's \"To collect\")") BigDecimal toCollect,
        @Schema(description = "Colour the owner picked (palette index 0-7), null = the app picks") Integer colour) {
}
