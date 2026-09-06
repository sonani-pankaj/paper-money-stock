package com.aigrama.papermoney.dto;

/**
 * Effective market data runtime configuration visible to dashboard.
 */
public record MarketConfigDto(
        String provider,
        long maxStaleSeconds
) {
}
