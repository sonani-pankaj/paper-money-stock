package com.aigrama.papermoney.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outbound model for strategy evaluation history.
 */
public record StrategyExecutionDto(
        String id,
        String strategyConfigId,
        String symbol,
        String side,
        BigDecimal triggerPrice,
        BigDecimal referencePrice,
        String orderId,
        String status,
        String message,
        LocalDateTime executedAt
) {
}
