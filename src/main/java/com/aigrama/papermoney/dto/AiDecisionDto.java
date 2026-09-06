package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * AI decision output used before placing an automated trade.
 */
public record AiDecisionDto(
        BigDecimal sentimentScore,
        BigDecimal volatilityPct,
        BigDecimal dynamicBuyDropPercent,
        BigDecimal dynamicSellRisePercent,
        BigDecimal buyProbability,
        BigDecimal sellProbability,
        boolean allowBuy,
        boolean allowSell
) {
}