package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.dto.MarketSymbolDto;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contract for market price retrieval and snapshot persistence.
 */
public interface MarketDataService {

    /**
     * Returns latest known price for symbol.
     */
    Mono<BigDecimal> getLatestPrice(String symbol);

    /**
     * Fetches price from provider and persists a new snapshot.
     */
    Mono<MarketSnapshotDto> refreshAndStore(String symbol);

    /**
     * Returns latest persisted snapshot for symbol.
     */
    Mono<MarketSnapshotDto> latestSnapshot(String symbol);

    /**
     * Searches symbols from the configured provider.
     */
    Mono<List<MarketSymbolDto>> searchSymbols(String query, int limit);
}
