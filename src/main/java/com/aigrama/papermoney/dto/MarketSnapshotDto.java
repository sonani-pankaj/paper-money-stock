package com.aigrama.papermoney.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outbound market snapshot model.
 */
public record MarketSnapshotDto(
        String symbol,
        BigDecimal price,
        LocalDateTime capturedAt
) {
}
