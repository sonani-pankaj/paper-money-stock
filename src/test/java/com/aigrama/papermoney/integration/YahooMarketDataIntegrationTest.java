package com.aigrama.papermoney.integration;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for Yahoo quote parsing and timestamp handling.
 */
@SpringBootTest(properties = {
        "paperstock.mode=simulator",
        "paperstock.market-data.provider=yahoo",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class YahooMarketDataIntegrationTest {

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
        registry.add("paperstock.market-data.yahoo.quote-base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.yahoo.chart-base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.yahoo.search-base-url", wireMockServer::baseUrl);
        registry.add("paperstock.market-data.twelvedata.base-url", wireMockServer::baseUrl);
        registry.add("paperstock.alpaca.base-url", wireMockServer::baseUrl);
    }
    @Test
    void refreshAndStoreShouldFallbackToYahooChartWhenYahooQuoteFails() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v7/finance/quote"))
                .willReturn(aResponse().withStatus(429)));

        wireMockServer.stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "chart": {
                                    "result": [
                                      {
                                        "meta": {
                                          "regularMarketPrice": 320.15,
                                          "regularMarketTime": 1735689660
                                        },
                                        "timestamp": [1735689600, 1735689660],
                                        "indicators": {
                                          "quote": [
                                            {
                                              "close": [319.97, 320.15]
                                            }
                                          ]
                                        }
                                      }
                                    ],
                                    "error": null
                                  }
                                }
                                """)));

        MarketSnapshotDto snapshot = marketDataService.refreshAndStore("AAPL").block();

        assertNotNull(snapshot);
        assertEquals("AAPL", snapshot.symbol());
        assertEquals(new BigDecimal("320.15"), snapshot.price());
        assertEquals(LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(1735689660), ZoneOffset.UTC), snapshot.capturedAt());
    }


    @Test
    void refreshAndStoreShouldParseYahooPriceAndTimestamp() {
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
        assertEquals(LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(1735689600), ZoneOffset.UTC), snapshot.capturedAt());
    }

      @Test
      void refreshAndStoreShouldFallbackToTwelveDataWhenYahooFails() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v7/finance/quote"))
            .willReturn(aResponse().withStatus(429)));

        wireMockServer.stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
            .willReturn(aResponse().withStatus(429)));

        wireMockServer.stubFor(get(urlEqualTo("/quote?symbol=AAPL&apikey=demo"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "symbol": "AAPL",
                      "name": "Apple Inc",
                      "close": "319.97",
                      "last_quote_at": 1735689600
                    }
                    """)));

        MarketSnapshotDto snapshot = marketDataService.refreshAndStore("AAPL").block();

        assertNotNull(snapshot);
        assertEquals("AAPL", snapshot.symbol());
        assertEquals(new BigDecimal("319.97"), snapshot.price());
        assertEquals(LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(1735689600), ZoneOffset.UTC), snapshot.capturedAt());
    }
}
