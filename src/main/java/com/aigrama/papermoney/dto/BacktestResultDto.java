package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Outcome metrics for one backtest mode.
 */
public record BacktestResultDto(
        String mode,
        int buyTrades,
        int sellTrades,
        BigDecimal endingCash,
        BigDecimal endingQty,
        BigDecimal endingValue,
        BigDecimal pnl
) {
}