package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Outbound order view model.
 */
public record OrderDto(
        String id,
        String symbol,
        String side,
        String type,
        String status,
        BigDecimal qty,
        BigDecimal filledQty,
        BigDecimal averageFillPrice
) {
}
