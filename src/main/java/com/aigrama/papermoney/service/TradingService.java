package com.aigrama.papermoney.service;

import com.aigrama.papermoney.adapter.TradingAdapter;
import com.aigrama.papermoney.dto.AccountDto;
import com.aigrama.papermoney.dto.ManualHoldingRequestDto;
import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.aigrama.papermoney.dto.PositionDto;
import com.aigrama.papermoney.entity.OrderEntity;
import com.aigrama.papermoney.entity.OrderSide;
import com.aigrama.papermoney.entity.OrderStatus;
import com.aigrama.papermoney.entity.OrderType;
import com.aigrama.papermoney.entity.PositionEntity;
import com.aigrama.papermoney.repository.OrderRepository;
import com.aigrama.papermoney.repository.PositionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Use-case service for validation, audit and delegation.
 */
@Service
public class TradingService {

    private final TradingAdapter tradingAdapter;
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final String mode;

    public TradingService(
            TradingAdapter tradingAdapter,
            OrderRepository orderRepository,
            PositionRepository positionRepository,
            @Value("${paperstock.mode:simulator}") String mode
    ) {
        this.tradingAdapter = tradingAdapter;
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.mode = mode;
    }

    public Mono<OrderDto> placeOrder(PlaceOrderRequestDto request) {
        validate(request);
        return persistAuditIfNeeded(request).then(tradingAdapter.placeOrder(request));
    }

    public Mono<OrderDto> getOrder(String orderId) {
        return tradingAdapter.getOrder(orderId);
    }

    public Mono<Void> cancelOrder(String orderId) {
        return tradingAdapter.cancelOrder(orderId);
    }

    public Flux<PositionDto> getPositions() {
        return tradingAdapter.getPositions();
    }

    public Mono<AccountDto> getAccount() {
        return tradingAdapter.getAccount();
    }

    public Mono<PositionDto> upsertManualHolding(ManualHoldingRequestDto request) {
        if (!"simulator".equalsIgnoreCase(mode)) {
            return Mono.error(new IllegalArgumentException("Manual holdings are only supported in simulator mode"));
        }

        if (request.qty() == null || request.qty().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("qty must be > 0"));
        }
        if (request.buyPrice() == null || request.buyPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("buyPrice must be > 0"));
        }

        String symbol = request.symbol().toUpperCase();
        return Mono.fromCallable(() -> {
                    PositionEntity position = positionRepository.findBySymbol(symbol).orElseGet(() -> {
                        PositionEntity created = new PositionEntity();
                        created.setId(UUID.randomUUID());
                        created.setSymbol(symbol);
                        return created;
                    });
                    position.setQty(request.qty());
                    position.setAveragePrice(request.buyPrice());
                    position.setUpdatedAt(LocalDateTime.now());
                    PositionEntity saved = positionRepository.save(position);
                    return new PositionDto(saved.getSymbol(), saved.getQty(), saved.getAveragePrice());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteManualHolding(String symbol) {
        if (!"simulator".equalsIgnoreCase(mode)) {
            return Mono.error(new IllegalArgumentException("Manual holdings are only supported in simulator mode"));
        }

        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalized.isBlank()) {
            return Mono.error(new IllegalArgumentException("symbol is required"));
        }

        return Mono.fromRunnable(() -> positionRepository.findBySymbol(normalized)
                        .ifPresent(positionRepository::delete))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void validate(PlaceOrderRequestDto request) {
        if (request.qty() == null || request.qty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }

        OrderType type = parseType(request.type());
        if (type == OrderType.LIMIT && request.limitPrice() == null) {
            throw new IllegalArgumentException("limitPrice is required for limit orders");
        }

        parseSide(request.side());
    }

    private Mono<Void> persistAuditIfNeeded(PlaceOrderRequestDto request) {
        if (!"alpaca".equalsIgnoreCase(mode)) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            OrderEntity entity = new OrderEntity();
            entity.setId(UUID.randomUUID());
            entity.setExternalId("PENDING");
            entity.setSymbol(request.symbol().toUpperCase());
            entity.setSide(parseSide(request.side()));
            entity.setType(parseType(request.type()));
            entity.setStatus(OrderStatus.NEW);
            entity.setQty(request.qty());
            entity.setFilledQty(BigDecimal.ZERO);
            entity.setLimitPrice(request.limitPrice());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(entity);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private OrderType parseType(String value) {
        return OrderType.valueOf(value.toUpperCase());
    }

    private OrderSide parseSide(String value) {
        return OrderSide.valueOf(value.toUpperCase());
    }
}
