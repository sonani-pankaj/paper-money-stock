package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Outbound position snapshot.
 */
public record PositionDto(
        String symbol,
        BigDecimal qty,
        BigDecimal averagePrice
) {
}
