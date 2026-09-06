package com.aigrama.papermoney.controller;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.dto.MarketSymbolDto;
import com.aigrama.papermoney.dto.MarketConfigDto;
import com.aigrama.papermoney.service.MarketDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * HTTP endpoints for market data queries.
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataService marketDataService;
    private final String provider;
    private final long maxStaleSeconds;

    public MarketController(
            MarketDataService marketDataService,
            @Value("${paperstock.market-data.provider:yahoo}") String provider,
            @Value("${paperstock.market-data.max-stale-seconds:120}") long maxStaleSeconds
    ) {
        this.marketDataService = marketDataService;
        this.provider = provider;
        this.maxStaleSeconds = maxStaleSeconds;
    }

    @GetMapping("/latest")
        public Mono<MarketSnapshotDto> latest(
            @RequestParam("symbol") String symbol,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh
        ) {
        if (refresh) {
                return marketDataService.refreshAndStore(symbol);
        }
        return marketDataService.latestSnapshot(symbol)
            .switchIfEmpty(marketDataService.refreshAndStore(symbol));
    }

    @GetMapping("/search")
    public Mono<List<MarketSymbolDto>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return marketDataService.searchSymbols(query, limit);
    }

    @GetMapping("/config")
    public MarketConfigDto config() {
        return new MarketConfigDto(provider, maxStaleSeconds);
    }
}
