package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.AccountDto;
import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.aigrama.papermoney.entity.OrderSide;
import com.aigrama.papermoney.entity.PositionEntity;
import com.aigrama.papermoney.entity.StrategyConfigEntity;
import com.aigrama.papermoney.entity.StrategyExecutionEntity;
import com.aigrama.papermoney.entity.StrategyExecutionStatus;
import com.aigrama.papermoney.repository.PositionRepository;
import com.aigrama.papermoney.repository.StrategyConfigRepository;
import com.aigrama.papermoney.repository.StrategyExecutionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates threshold strategies and places orders automatically.
 */
@Service
public class AutomatedStrategyService {

    private final StrategyConfigRepository strategyConfigRepository;
    private final StrategyExecutionRepository strategyExecutionRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final TradingService tradingService;
    private final String mode;
    private final long maxStaleSeconds;

    public AutomatedStrategyService(
            StrategyConfigRepository strategyConfigRepository,
            StrategyExecutionRepository strategyExecutionRepository,
            PositionRepository positionRepository,
            MarketDataService marketDataService,
            TradingService tradingService,
            @Value("${paperstock.mode:simulator}") String mode,
            @Value("${paperstock.market-data.max-stale-seconds:120}") long maxStaleSeconds
    ) {
        this.strategyConfigRepository = strategyConfigRepository;
        this.strategyExecutionRepository = strategyExecutionRepository;
        this.positionRepository = positionRepository;
        this.marketDataService = marketDataService;
        this.tradingService = tradingService;
        this.mode = mode;
        this.maxStaleSeconds = maxStaleSeconds;
    }

    public void evaluateAllActive() {
        List<StrategyConfigEntity> strategies = strategyConfigRepository.findAllByActiveTrue();
        strategies.forEach(strategy -> {
            try {
                evaluateOne(strategy);
            } catch (Exception ex) {
                record(strategy, OrderSide.BUY, null, null, null, StrategyExecutionStatus.FAILED, ex.getMessage());
            }
        });
    }

    @Transactional
    protected void evaluateOne(StrategyConfigEntity strategy) {
        if (!isModeAllowed(strategy)) {
            record(strategy, OrderSide.BUY, null, null, null, StrategyExecutionStatus.SKIPPED, "Strategy disabled for current mode");
            return;
        }

        if (isCooldownActive(strategy)) {
            record(strategy, OrderSide.BUY, null, null, null, StrategyExecutionStatus.SKIPPED, "Cooldown active");
            return;
        }

        long todayCount = successCountToday(strategy.getId());
        if (todayCount >= strategy.getMaxOrdersPerDay()) {
            record(strategy, OrderSide.BUY, null, null, null, StrategyExecutionStatus.SKIPPED, "Daily limit reached");
            return;
        }

        PositionEntity position = positionRepository.findBySymbol(strategy.getSymbol())
                .orElse(null);
        if (position == null || position.getAveragePrice() == null || position.getAveragePrice().compareTo(BigDecimal.ZERO) <= 0) {
            record(strategy, OrderSide.BUY, null, null, null, StrategyExecutionStatus.SKIPPED, "Missing position cost basis");
            return;
        }

        BigDecimal referencePrice = position.getAveragePrice();
        MarketSnapshotDto latestSnapshot = marketDataService.refreshAndStore(strategy.getSymbol())
            .block(Duration.ofSeconds(8));

        BigDecimal currentPrice = latestSnapshot == null ? null : latestSnapshot.price();

        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            record(strategy, OrderSide.BUY, null, referencePrice, null, StrategyExecutionStatus.SKIPPED, "No current market price");
            return;
        }

        if (latestSnapshot == null || latestSnapshot.capturedAt() == null) {
            record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "Missing quote timestamp");
            return;
        }

        long ageSeconds = ChronoUnit.SECONDS.between(latestSnapshot.capturedAt(), LocalDateTime.now());
        if (ageSeconds > maxStaleSeconds) {
            record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED,
                "Stale quote skipped: " + ageSeconds + "s old");
            return;
        }

        BigDecimal buyTriggerPrice = referencePrice
                .multiply(BigDecimal.ONE.subtract(strategy.getBuyDropPercent().movePointLeft(2)))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal sellTriggerPrice = referencePrice
                .multiply(BigDecimal.ONE.add(strategy.getSellRisePercent().movePointLeft(2)))
                .setScale(6, RoundingMode.HALF_UP);

        if (currentPrice.compareTo(buyTriggerPrice) <= 0) {
            executeBuy(strategy, currentPrice, referencePrice);
            return;
        }

        if (currentPrice.compareTo(sellTriggerPrice) >= 0) {
            executeSell(strategy, position, currentPrice, referencePrice);
            return;
        }

        record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "No threshold crossed");
    }

    private void executeBuy(StrategyConfigEntity strategy, BigDecimal currentPrice, BigDecimal referencePrice) {
        AccountDto account = tradingService.getAccount().block(Duration.ofSeconds(6));
        if (account == null || account.cash() == null || account.cash().compareTo(BigDecimal.ZERO) <= 0) {
            record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "No available cash");
            return;
        }

        BigDecimal cashToUse = account.cash().multiply(strategy.getBuyCashPercent().movePointLeft(2));
        BigDecimal qty = cashToUse.divide(currentPrice, 6, RoundingMode.DOWN);

        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "Computed buy qty is zero");
            return;
        }

        PlaceOrderRequestDto request = new PlaceOrderRequestDto(
                strategy.getSymbol(),
                qty,
                "buy",
                "market",
                null
        );

        OrderDto order = tradingService.placeOrder(request).block(Duration.ofSeconds(8));
        touchStrategyAction(strategy);
        record(strategy, OrderSide.BUY, order == null ? null : order.id(), currentPrice, referencePrice, StrategyExecutionStatus.SUCCESS, "BUY executed");
    }

    private void executeSell(
            StrategyConfigEntity strategy,
            PositionEntity position,
            BigDecimal currentPrice,
            BigDecimal referencePrice
    ) {
        if (position.getQty() == null || position.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            record(strategy, OrderSide.SELL, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "No position quantity to sell");
            return;
        }

        BigDecimal qty = position.getQty().multiply(strategy.getSellPositionPercent().movePointLeft(2))
                .setScale(6, RoundingMode.DOWN);

        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            record(strategy, OrderSide.SELL, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "Computed sell qty is zero");
            return;
        }

        PlaceOrderRequestDto request = new PlaceOrderRequestDto(
                strategy.getSymbol(),
                qty,
                "sell",
                "market",
                null
        );

        OrderDto order = tradingService.placeOrder(request).block(Duration.ofSeconds(8));
        touchStrategyAction(strategy);
        record(strategy, OrderSide.SELL, order == null ? null : order.id(), currentPrice, referencePrice, StrategyExecutionStatus.SUCCESS, "SELL executed");
    }

    private boolean isModeAllowed(StrategyConfigEntity strategy) {
        if ("simulator".equalsIgnoreCase(mode)) {
            return strategy.isSimulatorEnabled();
        }
        if ("alpaca".equalsIgnoreCase(mode)) {
            return strategy.isAlpacaEnabled();
        }
        return false;
    }

    private boolean isCooldownActive(StrategyConfigEntity strategy) {
        if (strategy.getLastActionAt() == null || strategy.getCooldownMinutes() == null || strategy.getCooldownMinutes() <= 0) {
            return false;
        }
        return strategy.getLastActionAt().plusMinutes(strategy.getCooldownMinutes()).isAfter(LocalDateTime.now());
    }

    private long successCountToday(UUID strategyId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return strategyExecutionRepository.countByStrategyConfigIdAndStatusAndExecutedAtBetween(
                strategyId,
                StrategyExecutionStatus.SUCCESS,
                start,
                end
        );
    }

    private void touchStrategyAction(StrategyConfigEntity strategy) {
        strategy.setLastActionAt(LocalDateTime.now());
        strategy.setUpdatedAt(LocalDateTime.now());
        strategyConfigRepository.save(strategy);
    }

    private void record(
            StrategyConfigEntity strategy,
            OrderSide side,
            String orderId,
            BigDecimal triggerPrice,
            BigDecimal referencePrice,
            StrategyExecutionStatus status,
            String message
    ) {
        StrategyExecutionEntity execution = new StrategyExecutionEntity();
        execution.setId(UUID.randomUUID());
        execution.setStrategyConfigId(strategy.getId());
        execution.setSymbol(strategy.getSymbol());
        execution.setSide(side);
        execution.setOrderId(orderId);
        execution.setTriggerPrice(triggerPrice);
        execution.setReferencePrice(referencePrice);
        execution.setStatus(status);
        execution.setMessage(message == null ? "" : message);
        execution.setExecutedAt(LocalDateTime.now());
        strategyExecutionRepository.save(execution);
    }
}
