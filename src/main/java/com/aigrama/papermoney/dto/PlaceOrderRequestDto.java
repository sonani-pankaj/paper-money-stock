package com.aigrama.papermoney.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request payload for order placement endpoint.
 */
public record PlaceOrderRequestDto(
        @NotBlank String symbol,
        @NotNull @DecimalMin("0.000001") BigDecimal qty,
        @NotBlank String side,
        @NotBlank String type,
        BigDecimal limitPrice
) {
}
