package com.aigrama.papermoney.dto;

/**
 * Pairwise comparison of AI-enabled and rule-only backtest outcomes.
 */
public record BacktestComparisonDto(
        String symbol,
        BacktestResultDto aiEnabled,
        BacktestResultDto ruleOnly
) {
}