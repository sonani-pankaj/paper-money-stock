package com.aigrama.papermoney.integration;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.dto.MarketSymbolDto;
import com.aigrama.papermoney.service.MarketDataService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for Finnhub quote parsing, symbol search, and fallback handling.
 */
@SpringBootTest(properties = {
        "paperstock.mode=simulator",
        "paperstock.market-data.provider=finnhub",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FinnhubMarketDataIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private MarketDataService marketDataService;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("paperstock.market-data.base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.finnhub.base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.finnhub.api-key", () -> "test_token");
        registry.add("paperstock.market-data.yahoo.quote-base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.yahoo.chart-base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.yahoo.search-base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.twelvedata.base-url", wireMockServer::baseUrl);
        registry.add("paperstock.alpaca.base-url", wireMockServer::baseUrl);
    }

    @Test
    void refreshAndStoreShouldParseFinnhubPriceAndTimestamp() {
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "c": 261.74,
                                  "h": 263.31,
                                  "l": 260.68,
                                  "o": 261.07,
                                  "pc": 259.45,
                                  "t": 1735689600
                                }
                                """)));

        MarketSnapshotDto snapshot = marketDataService.refreshAndStore("AAPL").block();

        assertNotNull(snapshot);
        assertEquals("AAPL", snapshot.symbol());
        assertEquals(new BigDecimal("261.74"), snapshot.price());
        assertEquals(LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(1735689600), ZoneOffset.UTC), snapshot.capturedAt());
    }

    @Test
    void refreshAndStoreShouldFallbackToYahooWhenFinnhubFails() {
        wireMockServer.stubFor(get(urlPathEqualTo("/quote"))
                .willReturn(aResponse().withStatus(429)));

        wireMockServer.stubFor(get(urlPathEqualTo("/v7/finance/quote"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "quoteResponse": {
                                    "result": [
                                      {
                                        "symbol": "AAPL",
                                        "regularMarketPrice": 233.41,
                                        "regularMarketTime": 1735689600
                                      }
                                    ],
                                    "error": null
                                  }
                                }
                                """)));

        MarketSnapshotDto snapshot = marketDataService.refreshAndStore("AAPL").block();

        assertNotNull(snapshot);
        assertEquals("AAPL", snapshot.symbol());
        assertEquals(new BigDecimal("233.41"), snapshot.price());
    }

    @Test
    void searchSymbolsShouldParseFinnhubResults() {
        wireMockServer.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "count": 2,
                                  "result": [
                                    {
                                      "description": "APPLE INC",
                                      "displaySymbol": "AAPL",
                                      "symbol": "AAPL",
                                      "type": "Common Stock"
                                    },
                                    {
                                      "description": "APPLE HOSPITALITY REIT INC",
                                      "displaySymbol": "APLE",
                                      "symbol": "APLE",
                                      "type": "Common Stock"
                                    }
                                  ]
                                }
                                """)));

        List<MarketSymbolDto> symbols = marketDataService.searchSymbols("AAPL", 10).block();

        assertNotNull(symbols);
        assertFalse(symbols.isEmpty());
        assertEquals(2, symbols.size());
        assertEquals("AAPL", symbols.get(0).symbol());
        assertEquals("APPLE INC", symbols.get(0).name());
    }
}
