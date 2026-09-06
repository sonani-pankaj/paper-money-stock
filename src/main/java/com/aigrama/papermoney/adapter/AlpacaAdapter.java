package com.aigrama.papermoney.adapter;

import com.aigrama.papermoney.dto.AccountDto;
import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.aigrama.papermoney.dto.PositionDto;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Alpaca-backed trading implementation.
 */
@Component
@ConditionalOnProperty(name = "paperstock.mode", havingValue = "alpaca")
public class AlpacaAdapter implements TradingAdapter {

    private final WebClient alpacaWebClient;
    private final io.github.resilience4j.retry.Retry retry;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;

    public AlpacaAdapter(
            @Qualifier("alpacaWebClient") WebClient alpacaWebClient,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.alpacaWebClient = alpacaWebClient;
        this.retry = retryRegistry.retry("alpacaClient");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("alpacaClient");
    }

    @Override
    public Mono<OrderDto> placeOrder(PlaceOrderRequestDto req) {
        Map<String, Object> body = Map.of(
                "symbol", req.symbol().toUpperCase(),
                "qty", req.qty(),
                "side", req.side().toLowerCase(),
                "type", req.type().toLowerCase(),
                "time_in_force", "day"
        );

        return alpacaWebClient.post()
                .uri("/v2/orders")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::mapOrder)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(Duration.ofSeconds(5));
    }

    @Override
    public Mono<OrderDto> getOrder(String orderId) {
        return alpacaWebClient.get()
                .uri("/v2/orders/{id}", orderId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::mapOrder)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(Duration.ofSeconds(5));
    }

    @Override
    public Mono<Void> cancelOrder(String orderId) {
        return alpacaWebClient.delete()
                .uri("/v2/orders/{id}", orderId)
                .retrieve()
                .bodyToMono(Void.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(Duration.ofSeconds(5));
    }

    @Override
    public Flux<PositionDto> getPositions() {
        return alpacaWebClient.get()
                .uri("/v2/positions")
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .map(this::mapPosition)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(Duration.ofSeconds(5));
    }

    @Override
    public Mono<AccountDto> getAccount() {
        return alpacaWebClient.get()
                .uri("/v2/account")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::mapAccount)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(Duration.ofSeconds(5));
    }

    /**
     * Example Alpaca order JSON shape:
     * {
     *   "id":"order-id",
     *   "symbol":"AAPL",
     *   "side":"buy",
     *   "type":"market",
     *   "status":"filled",
     *   "qty":"1",
     *   "filled_qty":"1",
     *   "filled_avg_price":"203.15"
     * }
     */
    private OrderDto mapOrder(JsonNode node) {
        return new OrderDto(
                node.path("id").asText(),
                node.path("symbol").asText(),
                node.path("side").asText(),
                node.path("type").asText(),
                node.path("status").asText(),
                decimal(node.path("qty").asText("0")),
                decimal(node.path("filled_qty").asText("0")),
                decimal(node.path("filled_avg_price").asText("0"))
        );
    }

    private PositionDto mapPosition(JsonNode node) {
        return new PositionDto(
                node.path("symbol").asText(),
                "alpaca",
                decimal(node.path("qty").asText("0")),
                decimal(node.path("avg_entry_price").asText("0"))
        );
    }

    private AccountDto mapAccount(JsonNode node) {
        return new AccountDto(
                "alpaca",
                decimal(node.path("cash").asText("0")),
                decimal(node.path("equity").asText("0"))
        );
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }
}
