package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.dto.MarketSymbolDto;
import com.aigrama.papermoney.entity.MarketSnapshotEntity;
import com.aigrama.papermoney.repository.MarketSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Free provider based market data implementation.
 */
@Service
public class FreeMarketDataService implements MarketDataService {

    private final WebClient marketDataWebClient;
    private final WebClient alpacaWebClient;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final String provider;
    private final String yahooQuoteBaseUrl;
    private final String yahooChartBaseUrl;
    private final String yahooSearchBaseUrl;
    private final String twelveDataBaseUrl;
    private final String twelveDataApiKey;

    public FreeMarketDataService(
            @Qualifier("marketDataWebClient") WebClient marketDataWebClient,
            @Qualifier("alpacaWebClient") WebClient alpacaWebClient,
            MarketSnapshotRepository marketSnapshotRepository,
            @Value("${paperstock.market-data.provider:yahoo}") String provider,
            @Value("${paperstock.market-data.yahoo.quote-base-url:https://query1.finance.yahoo.com}") String yahooQuoteBaseUrl,
            @Value("${paperstock.market-data.yahoo.chart-base-url:https://query2.finance.yahoo.com}") String yahooChartBaseUrl,
            @Value("${paperstock.market-data.yahoo.search-base-url:https://query2.finance.yahoo.com}") String yahooSearchBaseUrl,
            @Value("${paperstock.market-data.twelvedata.base-url:https://api.twelvedata.com}") String twelveDataBaseUrl,
            @Value("${paperstock.market-data.twelvedata.api-key:demo}") String twelveDataApiKey
    ) {
        this.marketDataWebClient = marketDataWebClient;
        this.alpacaWebClient = alpacaWebClient;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.provider = provider;
        this.yahooQuoteBaseUrl = yahooQuoteBaseUrl;
        this.yahooChartBaseUrl = yahooChartBaseUrl;
        this.yahooSearchBaseUrl = yahooSearchBaseUrl;
        this.twelveDataBaseUrl = twelveDataBaseUrl;
        this.twelveDataApiKey = twelveDataApiKey;
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
        return fetchQuote(symbol)
                .map(quote -> {
                    MarketSnapshotEntity entity = new MarketSnapshotEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setSymbol(symbol.toUpperCase());
                    entity.setPrice(quote.price());
                    entity.setCapturedAt(quote.timestamp());
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

    @Override
    public Mono<List<MarketSymbolDto>> searchSymbols(String query, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        if (query == null || query.isBlank()) {
            return Mono.just(List.of());
        }

        if ("alpaca".equalsIgnoreCase(provider)) {
            return searchSymbolsAlpaca(query, boundedLimit);
        }
        if ("yahoo".equalsIgnoreCase(provider)) {
            return searchSymbolsYahoo(query, boundedLimit);
        }
        return Mono.just(List.of());
    }

    private Mono<QuoteSnapshot> fetchQuote(String symbol) {
        if ("alpaca".equalsIgnoreCase(provider)) {
            return fetchQuoteAlpaca(symbol);
        }
        if ("yahoo".equalsIgnoreCase(provider)) {
            return fetchQuoteYahoo(symbol);
        }
        return fetchQuoteLegacy(symbol);
    }

    private Mono<QuoteSnapshot> fetchQuoteLegacy(String symbol) {
        return marketDataWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/latest")
                        .queryParam("base", "USD")
                        .queryParam("symbols", symbol.toUpperCase())
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    BigDecimal price = extractLegacyPrice(node, symbol);
                    return new QuoteSnapshot(price, LocalDateTime.now());
                });
    }

    private Mono<QuoteSnapshot> fetchQuoteYahoo(String symbol) {
        String url = yahooQuoteBaseUrl + "/v7/finance/quote?symbols=" + symbol.toUpperCase();
        return marketDataWebClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::extractYahooQuote)
            .onErrorResume(ex -> fetchQuoteYahooChart(symbol)
                .onErrorResume(chartEx -> fetchQuoteTwelveData(symbol)));
        }

        private Mono<QuoteSnapshot> fetchQuoteYahooChart(String symbol) {
        String normalized = symbol.toUpperCase(Locale.ROOT);
        String url = yahooChartBaseUrl + "/v8/finance/chart/" + normalized + "?range=1d&interval=1m";
        return marketDataWebClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::extractYahooChartQuote);
    }

    private Mono<QuoteSnapshot> fetchQuoteTwelveData(String symbol) {
        String url = twelveDataBaseUrl + "/quote?symbol=" + symbol.toUpperCase() + "&apikey=" + twelveDataApiKey;
        return marketDataWebClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::extractTwelveDataQuote);
    }

    private Mono<QuoteSnapshot> fetchQuoteAlpaca(String symbol) {
        return alpacaWebClient.get()
                .uri("/v2/stocks/{symbol}/quotes/latest", symbol.toUpperCase())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::extractAlpacaQuote);
    }

    private Mono<List<MarketSymbolDto>> searchSymbolsYahoo(String query, int limit) {
        String url = yahooSearchBaseUrl + "/v1/finance/search?q=" + query + "&quotesCount=" + limit + "&newsCount=0";
        return marketDataWebClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    List<MarketSymbolDto> result = new ArrayList<>();
                    JsonNode quotes = node.path("quotes");
                    if (quotes.isArray()) {
                        for (JsonNode quote : quotes) {
                            result.add(new MarketSymbolDto(
                                    quote.path("symbol").asText(""),
                                    quote.path("shortname").asText(quote.path("longname").asText("")),
                                    quote.path("exchange").asText(""),
                                    quote.path("quoteType").asText("")
                            ));
                        }
                    }
                    return result;
                })
                .onErrorReturn(List.of());
    }

    private Mono<List<MarketSymbolDto>> searchSymbolsAlpaca(String query, int limit) {
        return alpacaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/assets")
                        .queryParam("status", "active")
                        .queryParam("asset_class", "us_equity")
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    List<MarketSymbolDto> result = new ArrayList<>();
                    if (node.isArray()) {
                        int count = 0;
                        for (JsonNode item : node) {
                            if (count >= limit) {
                                break;
                            }
                            String symbol = item.path("symbol").asText("");
                            String name = item.path("name").asText("");
                            String exchange = item.path("exchange").asText("");
                            if (symbol.toUpperCase().contains(query.toUpperCase())
                                    || name.toUpperCase().contains(query.toUpperCase())) {
                                result.add(new MarketSymbolDto(symbol, name, exchange, "equity"));
                                count++;
                            }
                        }
                    }
                    return result;
                })
                .onErrorReturn(List.of());
    }

    private BigDecimal extractLegacyPrice(JsonNode node, String symbol) {
        JsonNode rates = node.path("rates");
        if (rates.isMissingNode() || rates.isEmpty()) {
            throw new IllegalStateException("Legacy provider rates are empty");
        }
        JsonNode symbolRate = rates.path(symbol.toUpperCase());
        if (symbolRate.isMissingNode() || !symbolRate.isNumber()) {
            throw new IllegalStateException("Legacy provider missing numeric rate for symbol " + symbol.toUpperCase());
        }
        return symbolRate.decimalValue();
    }

    private QuoteSnapshot extractYahooQuote(JsonNode node) {
        JsonNode first = node.path("quoteResponse").path("result");
        if (!first.isArray() || first.size() == 0) {
            throw new IllegalStateException("Yahoo quote result is empty");
        }
        JsonNode quote = first.get(0);
        if (!quote.path("regularMarketPrice").isNumber()) {
            throw new IllegalStateException("Yahoo regularMarketPrice missing");
        }
        BigDecimal price = quote.path("regularMarketPrice").decimalValue();
        long epoch = quote.path("regularMarketTime").asLong(0L);
        LocalDateTime timestamp = epoch > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC)
                : LocalDateTime.now();
        return new QuoteSnapshot(price, timestamp);
    }

    private QuoteSnapshot extractTwelveDataQuote(JsonNode node) {
        String closeText = node.path("close").asText("");
        if (closeText.isBlank()) {
            throw new IllegalStateException("TwelveData close price missing");
        }

        BigDecimal price = new BigDecimal(closeText);

        long epoch = node.path("last_quote_at").asLong(0L);
        if (epoch > 0) {
            return new QuoteSnapshot(price, LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC));
        }

        String datetime = node.path("datetime").asText("");
        if (!datetime.isBlank()) {
            LocalDateTime parsed = LocalDateTime.parse(datetime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return new QuoteSnapshot(price, parsed);
        }

        return new QuoteSnapshot(price, LocalDateTime.now());
    }

    private QuoteSnapshot extractYahooChartQuote(JsonNode node) {
        JsonNode resultArray = node.path("chart").path("result");
        if (!resultArray.isArray() || resultArray.isEmpty()) {
            throw new IllegalStateException("Yahoo chart result is empty");
        }

        JsonNode result = resultArray.get(0);
        JsonNode timestamps = result.path("timestamp");
        JsonNode close = result.path("indicators").path("quote").path(0).path("close");

        if (!timestamps.isArray() || !close.isArray()) {
            throw new IllegalStateException("Yahoo chart missing timestamp/close arrays");
        }

        int maxIndex = Math.min(timestamps.size(), close.size()) - 1;
        for (int i = maxIndex; i >= 0; i--) {
            JsonNode closeNode = close.get(i);
            JsonNode tsNode = timestamps.get(i);
            if (closeNode != null && closeNode.isNumber() && tsNode != null && tsNode.canConvertToLong()) {
                BigDecimal price = closeNode.decimalValue();
                LocalDateTime timestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(tsNode.asLong()), ZoneOffset.UTC);
                return new QuoteSnapshot(price, timestamp);
            }
        }

        JsonNode meta = result.path("meta");
        if (meta.path("regularMarketPrice").isNumber()) {
            BigDecimal price = meta.path("regularMarketPrice").decimalValue();
            long epoch = meta.path("regularMarketTime").asLong(0L);
            LocalDateTime timestamp = epoch > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC)
                    : LocalDateTime.now();
            return new QuoteSnapshot(price, timestamp);
        }

        throw new IllegalStateException("Yahoo chart did not contain a usable live quote");
    }

    private QuoteSnapshot extractAlpacaQuote(JsonNode node) {
        JsonNode quote = node.path("quote");
        BigDecimal ask = quote.path("ap").isNumber()
                ? quote.path("ap").decimalValue()
                : BigDecimal.ZERO;
        BigDecimal bid = quote.path("bp").isNumber()
                ? quote.path("bp").decimalValue()
                : BigDecimal.ZERO;
        BigDecimal price = ask.compareTo(BigDecimal.ZERO) > 0 ? ask : bid;
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Alpaca quote missing bid/ask price");
        }
        String ts = quote.path("t").asText("");
        LocalDateTime timestamp;
        try {
            timestamp = ts.isBlank() ? LocalDateTime.now() : LocalDateTime.ofInstant(Instant.parse(ts), ZoneOffset.UTC);
        } catch (Exception ex) {
            timestamp = LocalDateTime.now();
        }
        return new QuoteSnapshot(price, timestamp);
    }

    private record QuoteSnapshot(BigDecimal price, LocalDateTime timestamp) {
    }
}
