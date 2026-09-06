package com.aigrama.papermoney.integration;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.service.MarketDataService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for Alpaca quote parsing and timestamp handling.
 */
@SpringBootTest(properties = {
        "paperstock.mode=simulator",
        "paperstock.market-data.provider=alpaca",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AlpacaMarketDataIntegrationTest {

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

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("paperstock.market-data.base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.yahoo.quote-base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.yahoo.search-base-url", wireMockServer::baseUrl);
        registry.add("paperstock.alpaca.base-url", wireMockServer::baseUrl);
    }

    @Test
    void refreshAndStoreShouldParseAlpacaPriceAndTimestamp() {
        wireMockServer.stubFor(get(urlEqualTo("/v2/stocks/AAPL/quotes/latest"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "symbol": "AAPL",
                                  "quote": {
                                    "ap": 226.55,
                                    "bp": 226.48,
                                    "t": "2025-01-01T15:30:00Z"
                                  }
                                }
                                """)));

        MarketSnapshotDto snapshot = marketDataService.refreshAndStore("AAPL").block();

        assertNotNull(snapshot);
        assertEquals("AAPL", snapshot.symbol());
        assertEquals(new BigDecimal("226.55"), snapshot.price());
        assertEquals(LocalDateTime.ofInstant(java.time.Instant.parse("2025-01-01T15:30:00Z"), ZoneOffset.UTC), snapshot.capturedAt());
    }
}
