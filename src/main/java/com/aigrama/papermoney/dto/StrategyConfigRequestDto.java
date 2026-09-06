package com.aigrama.papermoney.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request payload for strategy create/update.
 */
public record StrategyConfigRequestDto(
        @NotBlank String symbol,
        @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal buyDropPercent,
        @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal sellRisePercent,
        @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal buyCashPercent,
        @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal sellPositionPercent,
        @NotNull @Min(1) Integer maxOrdersPerDay,
        @NotNull @Min(0) Integer cooldownMinutes,
        @NotNull Boolean active,
        @NotNull Boolean simulatorEnabled,
        @NotNull Boolean alpacaEnabled
) {
}
