package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Outbound trade view model.
 */
public record TradeDto(
        String id,
        String orderId,
        String symbol,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal fee
) {
}
