package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.entity.MarketSnapshotEntity;
import com.aigrama.papermoney.repository.MarketSnapshotRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Free provider based market data implementation.
 */
@Service
public class FreeMarketDataService implements MarketDataService {

    private final WebClient marketDataWebClient;
    private final MarketSnapshotRepository marketSnapshotRepository;

    public FreeMarketDataService(
            @Qualifier("marketDataWebClient") WebClient marketDataWebClient,
            MarketSnapshotRepository marketSnapshotRepository
    ) {
        this.marketDataWebClient = marketDataWebClient;
        this.marketSnapshotRepository = marketSnapshotRepository;
    }

    @Override
    @Cacheable(cacheNames = "marketData", key = "#symbol")
    public Mono<BigDecimal> getLatestPrice(String symbol) {
        return latestSnapshot(symbol)
                .map(MarketSnapshotDto::price)
                .switchIfEmpty(refreshAndStore(symbol).map(MarketSnapshotDto::price));
    }

    @Override
    public Mono<MarketSnapshotDto> refreshAndStore(String symbol) {
        return fetchPrice(symbol)
                .map(price -> {
                    MarketSnapshotEntity entity = new MarketSnapshotEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setSymbol(symbol.toUpperCase());
                    entity.setPrice(price);
                    entity.setCapturedAt(LocalDateTime.now());
                    MarketSnapshotEntity saved = marketSnapshotRepository.save(entity);
                    return new MarketSnapshotDto(saved.getSymbol(), saved.getPrice(), saved.getCapturedAt());
                });
    }

    @Override
    public Mono<MarketSnapshotDto> latestSnapshot(String symbol) {
        return Mono.fromCallable(() -> marketSnapshotRepository.findTopBySymbolOrderByCapturedAtDesc(symbol.toUpperCase()))
                .flatMap(optional -> optional
                        .map(entity -> Mono.just(new MarketSnapshotDto(entity.getSymbol(), entity.getPrice(), entity.getCapturedAt())))
                        .orElseGet(Mono::empty));
    }

    private Mono<BigDecimal> fetchPrice(String symbol) {
        return marketDataWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/latest")
                        .queryParam("base", "USD")
                        .queryParam("symbols", symbol.toUpperCase())
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> extractPrice(node, symbol))
                .onErrorResume(ex -> fallbackFromDatabase(symbol));
    }

    private BigDecimal extractPrice(JsonNode node, String symbol) {
        JsonNode rates = node.path("rates");
        if (rates.isMissingNode() || rates.isEmpty()) {
            return BigDecimal.valueOf(100);
        }
        JsonNode symbolRate = rates.path(symbol.toUpperCase());
        if (symbolRate.isMissingNode() || !symbolRate.isNumber()) {
            return BigDecimal.valueOf(100);
        }
        return symbolRate.decimalValue();
    }

    private Mono<BigDecimal> fallbackFromDatabase(String symbol) {
        return latestSnapshot(symbol)
                .map(MarketSnapshotDto::price)
                .switchIfEmpty(Mono.just(BigDecimal.valueOf(100)));
    }
}
