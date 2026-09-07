package com.aigrama.papermoney.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outbound strategy configuration model.
 */
public record StrategyConfigDto(
        String id,
        String symbol,
        BigDecimal buyDropPercent,
        BigDecimal sellRisePercent,
        BigDecimal buyCashPercent,
        BigDecimal sellPositionPercent,
        Integer maxOrdersPerDay,
        Integer cooldownMinutes,
        boolean active,
        /** The single broker this strategy executes on. */
        String broker,
        LocalDateTime lastActionAt,
        BigDecimal currentPrice,
        BigDecimal referencePrice,
        BigDecimal buyTriggerPrice,
        BigDecimal sellTriggerPrice
) {
}
