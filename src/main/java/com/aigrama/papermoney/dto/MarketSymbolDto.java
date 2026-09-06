package com.aigrama.papermoney.dto;

/**
 * Search result item for stock symbols.
 */
public record MarketSymbolDto(
        String symbol,
        String name,
        String exchange,
        String type
) {
}
