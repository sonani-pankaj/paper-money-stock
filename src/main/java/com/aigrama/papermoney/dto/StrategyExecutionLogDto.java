package com.aigrama.papermoney.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Individual strategy execution row for the trade history report.
 * One row per executed BUY or SELL, with price, qty, and total amount.
 */
public record StrategyExecutionLogDto(
        String id,
        LocalDateTime executedAt,
        String symbol,
        String side,        // "BUY" or "SELL"
        String broker,
        BigDecimal price,   // triggerPrice (price at execution)
        BigDecimal qty,     // shares filled (from TradeEntity)
        BigDecimal amount   // price × qty
) {
}
