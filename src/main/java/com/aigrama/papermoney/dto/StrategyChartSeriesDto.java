package com.aigrama.papermoney.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Chart data model for strategy threshold visualization.
 */
public record StrategyChartSeriesDto(
        String strategyId,
        String symbol,
        BigDecimal referencePrice,
        BigDecimal buyDropPercent,
        BigDecimal sellRisePercent,
        List<StrategyChartPointDto> points
) {
}
