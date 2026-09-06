package com.aigrama.papermoney.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request to manually add or update a holding row.
 */
public record ManualHoldingRequestDto(
        @NotBlank String symbol,
        @NotNull @DecimalMin("0.000001") BigDecimal qty,
        @NotNull @DecimalMin("0.000001") BigDecimal buyPrice
) {
}
