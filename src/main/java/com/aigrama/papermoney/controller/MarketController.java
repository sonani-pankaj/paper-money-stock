package com.aigrama.papermoney.controller;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.service.MarketDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * HTTP endpoints for market data queries.
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataService marketDataService;

    public MarketController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/latest")
    public Mono<MarketSnapshotDto> latest(@RequestParam("symbol") String symbol) {
        return marketDataService.latestSnapshot(symbol)
                .switchIfEmpty(marketDataService.refreshAndStore(symbol));
    }
}
