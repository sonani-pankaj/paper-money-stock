package com.aigrama.papermoney.adapter;

import com.aigrama.papermoney.dto.AccountDto;
import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.aigrama.papermoney.dto.PositionDto;
import com.aigrama.papermoney.entity.AccountStateEntity;
import com.aigrama.papermoney.entity.OrderEntity;
import com.aigrama.papermoney.entity.OrderSide;
import com.aigrama.papermoney.entity.OrderStatus;
import com.aigrama.papermoney.entity.OrderType;
import com.aigrama.papermoney.entity.PositionEntity;
import com.aigrama.papermoney.repository.AccountStateRepository;
import com.aigrama.papermoney.repository.OrderRepository;
import com.aigrama.papermoney.repository.PositionRepository;
import com.aigrama.papermoney.repository.TradeRepository;
import com.aigrama.papermoney.service.MarketDataService;
import com.aigrama.papermoney.simulator.MatchingEngine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Local simulation-backed trading implementation.
 */
@Component
public class SimulatorAdapter implements TradingAdapter {

    private final MatchingEngine matchingEngine;
    private final MarketDataService marketDataService;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final PositionRepository positionRepository;
    private final AccountStateRepository accountStateRepository;

    public SimulatorAdapter(
            MatchingEngine matchingEngine,
            MarketDataService marketDataService,
            OrderRepository orderRepository,
            TradeRepository tradeRepository,
            PositionRepository positionRepository,
            AccountStateRepository accountStateRepository
    ) {
        this.matchingEngine = matchingEngine;
        this.marketDataService = marketDataService;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.positionRepository = positionRepository;
        this.accountStateRepository = accountStateRepository;
    }

    @Override
    public Mono<OrderDto> placeOrder(PlaceOrderRequestDto req) {
        // Do NOT call refreshAndStore here — in simulator mode the caller may have
        // injected a specific price via SimulatorController; refreshing would overwrite it.
        return Mono.fromCallable(() -> placeOrderTx(req))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<OrderDto> getOrder(String orderId) {
        return Mono.fromCallable(() -> orderRepository.findById(UUID.fromString(orderId))
                        .map(this::mapOrder)
                        .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> cancelOrder(String orderId) {
        return Mono.fromRunnable(() -> cancelOrderTx(orderId)).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Flux<PositionDto> getPositions() {
        return Flux.defer(() -> Flux.fromIterable(positionRepository.findAll())
                .map(this::mapPosition))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<AccountDto> getAccount() {
        return Mono.fromCallable(() -> {
            AccountStateEntity accountState = getOrCreateAccountState();
            BigDecimal equity = positionRepository.findAll().stream()
                    .map(position -> position.getAveragePrice().multiply(position.getQty()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AccountDto(accountState.getMode(), accountState.getCash(), equity);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    protected OrderDto placeOrderTx(PlaceOrderRequestDto req) {
        OrderEntity order = new OrderEntity();
        order.setId(UUID.randomUUID());
        order.setSymbol(req.symbol().toUpperCase());
        order.setSide(OrderSide.valueOf(req.side().toUpperCase()));
        order.setType(OrderType.valueOf(req.type().toUpperCase()));
        order.setStatus(OrderStatus.NEW);
        order.setQty(req.qty());
        order.setFilledQty(BigDecimal.ZERO);
        order.setLimitPrice(req.limitPrice());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        order = orderRepository.save(order);
        MatchingEngine.MatchResult result = matchingEngine.match(order);

        if (result.filledQty().compareTo(BigDecimal.ZERO) > 0) {
            order.setFilledQty(result.filledQty());
            order.setAverageFillPrice(result.fillPrice());
            boolean fullyFilled = result.filledQty().compareTo(order.getQty()) >= 0;
            order.setStatus(fullyFilled ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            tradeRepository.saveAll(result.trades());
            updatePosition(order, result.filledQty(), result.fillPrice());
        }

        return mapOrder(order);
    }

    @Transactional
    protected void cancelOrderTx(String orderId) {
        OrderEntity order = orderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.FILLED) {
            throw new IllegalStateException("Filled order cannot be canceled");
        }
        order.setStatus(OrderStatus.CANCELED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    private void updatePosition(OrderEntity order, BigDecimal fillQty, BigDecimal fillPrice) {
        PositionEntity position = positionRepository.findBySymbol(order.getSymbol())
                .orElseGet(() -> {
                    PositionEntity entity = new PositionEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setSymbol(order.getSymbol());
                    entity.setQty(BigDecimal.ZERO);
                    entity.setAveragePrice(BigDecimal.ZERO);
                    entity.setUpdatedAt(LocalDateTime.now());
                    return entity;
                });

        BigDecimal signedQty = order.getSide() == OrderSide.BUY ? fillQty : fillQty.negate();
        BigDecimal newQty = position.getQty().add(signedQty);

        if (order.getSide() == OrderSide.BUY && newQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal prevValue = position.getAveragePrice().multiply(position.getQty());
            BigDecimal fillValue = fillPrice.multiply(fillQty);
            BigDecimal newAvg = prevValue.add(fillValue).divide(newQty, 6, RoundingMode.HALF_UP);
            position.setAveragePrice(newAvg);
        }

        AccountStateEntity accountState = getOrCreateAccountState();
        position.setMode(accountState.getMode() != null ? accountState.getMode() : "simulator");
        position.setQty(newQty);
        position.setUpdatedAt(LocalDateTime.now());
        positionRepository.save(position);

        BigDecimal tradeAmount = fillPrice.multiply(fillQty);
        if (order.getSide() == OrderSide.BUY) {
            accountState.setCash(accountState.getCash().subtract(tradeAmount).max(BigDecimal.ZERO));
        } else if (order.getSide() == OrderSide.SELL) {
            accountState.setCash(accountState.getCash().add(tradeAmount));
        }
        accountState.setUpdatedAt(LocalDateTime.now());
        accountStateRepository.save(accountState);
    }

    private AccountStateEntity getOrCreateAccountState() {
        return accountStateRepository.findById("DEFAULT").orElseGet(() -> {
            AccountStateEntity entity = new AccountStateEntity();
            entity.setId("DEFAULT");
            entity.setMode("simulator");
            entity.setCash(BigDecimal.valueOf(100_000));
            entity.setUpdatedAt(LocalDateTime.now());
            return accountStateRepository.save(entity);
        });
    }

    private OrderDto mapOrder(OrderEntity entity) {
        return new OrderDto(
                entity.getId().toString(),
                entity.getSymbol(),
                entity.getSide().name().toLowerCase(),
                entity.getType().name().toLowerCase(),
                entity.getStatus().name().toLowerCase(),
                entity.getQty(),
                entity.getFilledQty(),
                entity.getAverageFillPrice()
        );
    }

    private PositionDto mapPosition(PositionEntity entity) {
        return new PositionDto(
                entity.getSymbol(),
                entity.getMode() != null ? entity.getMode() : "simulator",
                entity.getQty(),
                entity.getAveragePrice()
        );
    }
}
