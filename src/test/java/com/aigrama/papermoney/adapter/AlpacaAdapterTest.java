package com.aigrama.papermoney.adapter;

import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WireMock-based tests for Alpaca adapter mappings.
 */
class AlpacaAdapterTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void placeOrderShouldMapAlpacaResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/v2/orders"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id":"alpaca-1",
                                  "symbol":"AAPL",
                                  "side":"buy",
                                  "type":"market",
                                  "status":"filled",
                                  "qty":"1",
                                  "filled_qty":"1",
                                  "filled_avg_price":"190.25"
                                }
                                """)));

        WebClient client = WebClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .build();

        AlpacaAdapter adapter = new AlpacaAdapter(
                client,
                RetryRegistry.ofDefaults(),
                CircuitBreakerRegistry.ofDefaults()
        );

        OrderDto dto = adapter.placeOrder(new PlaceOrderRequestDto("AAPL", BigDecimal.ONE, "buy", "market", null)).block();

        assertEquals("alpaca-1", dto.id());
        assertEquals(new BigDecimal("190.25"), dto.averageFillPrice());
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v2/orders")).withRequestBody(equalToJson("""
                {
                  "symbol": "AAPL",
                  "qty": 1,
                  "side": "buy",
                  "type": "market",
                  "time_in_force": "day"
                }
                """, true, true)));
    }
}
