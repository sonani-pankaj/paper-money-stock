package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Outbound position snapshot.
 */
public record PositionDto(
        String symbol,
        String mode,
        BigDecimal qty,
        BigDecimal averagePrice
) {
}
