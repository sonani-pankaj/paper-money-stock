package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Aggregated sentiment score view for one symbol.
 */
public record SentimentScoreDto(
        String symbol,
        BigDecimal score,
        int sampleSize
) {
}