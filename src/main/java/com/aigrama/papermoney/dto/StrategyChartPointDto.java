package com.aigrama.papermoney.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One point in the strategy chart series.
 */
public record StrategyChartPointDto(
        LocalDateTime capturedAt,
        BigDecimal price,
        BigDecimal buyTriggerPrice,
        BigDecimal sellTriggerPrice
) {
}
