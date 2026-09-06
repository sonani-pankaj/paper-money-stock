package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Aggregated strategy trade report grouped by symbol.
 */
public record StrategyTradeReportDto(
        String symbol,
        BigDecimal buyQty,
        BigDecimal buyAmount,
        long buyTrades,
        BigDecimal sellQty,
        BigDecimal sellAmount,
        long sellTrades
) {
}