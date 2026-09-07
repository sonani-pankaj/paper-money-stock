package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Aggregated strategy trade report grouped by symbol and broker.
 */
public record StrategyTradeReportDto(
        String symbol,
        String broker,
        BigDecimal buyQty,
        BigDecimal buyAmount,
        long buyTrades,
        BigDecimal sellQty,
        BigDecimal sellAmount,
        long sellTrades
) {
}