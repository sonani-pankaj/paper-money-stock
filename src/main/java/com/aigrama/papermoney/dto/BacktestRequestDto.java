package com.aigrama.papermoney.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Input for AI vs rule-only strategy backtest.
 */
public record BacktestRequestDto(
        @NotBlank String symbol,
        @NotNull @DecimalMin("0.01") BigDecimal initialCash,
        @NotNull @DecimalMin("0.01") BigDecimal buyDropPercent,
        @NotNull @DecimalMin("0.01") BigDecimal sellRisePercent,
        @NotNull @DecimalMin("0.01") BigDecimal buyCashPercent,
        @NotNull @DecimalMin("0.01") BigDecimal sellPositionPercent,
        Integer maxPoints
) {
}