package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.AccountDto;
import com.aigrama.papermoney.dto.AiDecisionDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AutomatedStrategyService.class);

    private final StrategyConfigRepository strategyConfigRepository;
    private final StrategyExecutionRepository strategyExecutionRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final TradingService tradingService;
    private final AiDecisionService aiDecisionService;
    private final SimulatorPriceRegistry simulatorPriceRegistry;
    private final String mode;
    private final long maxStaleSeconds;
    private final boolean aiEnabled;

    public AutomatedStrategyService(
            StrategyConfigRepository strategyConfigRepository,
            StrategyExecutionRepository strategyExecutionRepository,
            PositionRepository positionRepository,
            MarketDataService marketDataService,
            TradingService tradingService,
            AiDecisionService aiDecisionService,
            SimulatorPriceRegistry simulatorPriceRegistry,
            @Value("${paperstock.mode:simulator}") String mode,
            @Value("${paperstock.market-data.max-stale-seconds:120}") long maxStaleSeconds,
            @Value("${paperstock.ai.enabled:true}") boolean aiEnabled
    ) {
        this.strategyConfigRepository = strategyConfigRepository;
        this.strategyExecutionRepository = strategyExecutionRepository;
        this.positionRepository = positionRepository;
        this.marketDataService = marketDataService;
        this.tradingService = tradingService;
        this.aiDecisionService = aiDecisionService;
        this.simulatorPriceRegistry = simulatorPriceRegistry;
        this.mode = mode;
        this.maxStaleSeconds = maxStaleSeconds;
        this.aiEnabled = aiEnabled;
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
            record(strategy, OrderSide.BUY, null, null, null, StrategyExecutionStatus.SKIPPED,
                    "Strategy broker '" + strategy.getBroker() + "' does not match active mode '" + mode + "'");
            return;
        }

        // Cooldown and daily limit only guard BUY (checked below). SELL is never blocked.

        PositionEntity position = positionRepository.findBySymbol(strategy.getSymbol())
                .orElse(null);

        // Use the stored snapshot so manually injected simulator prices are respected.
        MarketSnapshotDto latestSnapshot = marketDataService.latestSnapshot(strategy.getSymbol())
                .switchIfEmpty(marketDataService.refreshAndStore(strategy.getSymbol()))
                .block(Duration.ofSeconds(8));

        BigDecimal currentPrice = latestSnapshot == null ? null : latestSnapshot.price();
        log.info("[{}] eval: currentPrice={} capturedAt={}", strategy.getSymbol(), currentPrice,
                latestSnapshot != null ? latestSnapshot.capturedAt() : "null");

        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[{}] SKIP — no current market price", strategy.getSymbol());
            record(strategy, OrderSide.BUY, null, null, null, StrategyExecutionStatus.SKIPPED, "No current market price");
            return;
        }

        // Stable reference: (1) position avg price, (2) baseline at creation, (3) current price
        BigDecimal referencePrice;
        if (position != null && position.getAveragePrice() != null && position.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
            referencePrice = position.getAveragePrice();
        } else if (strategy.getBaselinePrice() != null && strategy.getBaselinePrice().compareTo(BigDecimal.ZERO) > 0) {
            referencePrice = strategy.getBaselinePrice();
        } else {
            referencePrice = currentPrice;
        }
        log.info("[{}] eval: referencePrice={} baselinePrice={}", strategy.getSymbol(), referencePrice, strategy.getBaselinePrice());

        if (latestSnapshot == null || latestSnapshot.capturedAt() == null) {
            log.warn("[{}] SKIP — missing quote timestamp", strategy.getSymbol());
            record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "Missing quote timestamp");
            return;
        }

        long ageSeconds = ChronoUnit.SECONDS.between(latestSnapshot.capturedAt(), LocalDateTime.now());
        if (ageSeconds > maxStaleSeconds) {
            // If this is a pinned simulator price, do NOT refresh from the real market API
            // because that would overwrite the manually injected test price.
            if (simulatorPriceRegistry.isPinned(strategy.getSymbol())) {
                log.info("[{}] quote is stale ({}s) but price is pinned — skipping refresh",
                        strategy.getSymbol(), ageSeconds);
                // Continue evaluation with the pinned price as-is
            } else {
                log.warn("[{}] quote is stale ({}s old) — refreshing from market API", strategy.getSymbol(), ageSeconds);
                latestSnapshot = marketDataService.refreshAndStore(strategy.getSymbol()).block(Duration.ofSeconds(8));
                if (latestSnapshot == null) {
                    log.warn("[{}] SKIP — refresh failed, no market price available", strategy.getSymbol());
                    record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "Stale quote, refresh failed");
                    return;
                }
                currentPrice = latestSnapshot.price();
                log.info("[{}] refreshed price: {}", strategy.getSymbol(), currentPrice);
            }
        }

        BigDecimal buyDropPercent = strategy.getBuyDropPercent();
        BigDecimal sellRisePercent = strategy.getSellRisePercent();
        boolean allowBuy = true;
        boolean allowSell = true;

        // AI gate: skip in simulator mode — the user is testing strategy thresholds
        // directly, not AI sentiment. Injecting a lower price creates artificial negative
        // momentum which would veto a valid buy signal.
        boolean isSimulator = "simulator".equalsIgnoreCase(strategy.getBroker())
                || "simulator".equalsIgnoreCase(mode);

        if (aiEnabled && !isSimulator) {
            AiDecisionDto decision = aiDecisionService.evaluate(
                strategy.getSymbol(),
                strategy.getBuyDropPercent(),
                strategy.getSellRisePercent(),
                currentPrice
            );
            buyDropPercent = decision.dynamicBuyDropPercent();
            sellRisePercent = decision.dynamicSellRisePercent();
            allowBuy = decision.allowBuy();
            allowSell = decision.allowSell();
            log.info("[{}] AI: allowBuy={} allowSell={} buyProb={} sellProb={}",
                    strategy.getSymbol(), allowBuy, allowSell,
                    decision.buyProbability(), decision.sellProbability());
        } else if (isSimulator) {
            log.info("[{}] AI gate bypassed (simulator mode)", strategy.getSymbol());
        }

        BigDecimal buyTriggerPrice = referencePrice
            .multiply(BigDecimal.ONE.subtract(buyDropPercent.movePointLeft(2)))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal sellTriggerPrice = referencePrice
            .multiply(BigDecimal.ONE.add(sellRisePercent.movePointLeft(2)))
                .setScale(6, RoundingMode.HALF_UP);

        log.info("[{}] eval: buyTrigger={} sellTrigger={} current={}",
                strategy.getSymbol(), buyTriggerPrice, sellTriggerPrice, currentPrice);

        if (!allowBuy && currentPrice.compareTo(buyTriggerPrice) <= 0) {
            record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED,
                "AI gate blocked BUY signal");
            return;
        }

        if (!allowSell && currentPrice.compareTo(sellTriggerPrice) >= 0) {
            record(strategy, OrderSide.SELL, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED,
                "AI gate blocked SELL signal");
            return;
        }

        if (currentPrice.compareTo(buyTriggerPrice) <= 0) {
            // Side-aware cooldown: only blocks BUY→BUY repeats (not BUY after a SELL).
            if (isSideCooldownActive(strategy, "BUY")) {
                log.info("[{}] SKIP BUY — same-side cooldown active (last: {} at {})",
                        strategy.getSymbol(), strategy.getLastActionSide(), strategy.getLastActionAt());
                record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "Cooldown active");
                return;
            }
            long todayBuys = successCountToday(strategy.getId());
            if (todayBuys >= strategy.getMaxOrdersPerDay()) {
                log.info("[{}] SKIP BUY — daily limit reached ({}/{})", strategy.getSymbol(), todayBuys, strategy.getMaxOrdersPerDay());
                record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "Daily limit reached");
                return;
            }
            executeBuy(strategy, currentPrice, referencePrice);
            return;
        }

        // SELL: side-aware cooldown blocks SELL→SELL repeats (stops infinite re-sell of partial positions).
        // BUY→SELL transition is always allowed instantly.
        if (currentPrice.compareTo(sellTriggerPrice) >= 0) {
            if (isSideCooldownActive(strategy, "SELL")) {
                log.info("[{}] SKIP SELL — same-side cooldown active (last SELL: {})", strategy.getSymbol(), strategy.getLastActionAt());
                record(strategy, OrderSide.SELL, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "Sell cooldown active");
                return;
            }
            log.info("[{}] SELL trigger met (current={} >= sell={})", strategy.getSymbol(), currentPrice, sellTriggerPrice);
            executeSell(strategy, position, currentPrice, referencePrice);
            return;
        }

        record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "No threshold crossed");
    }

    private void executeBuy(StrategyConfigEntity strategy, BigDecimal currentPrice, BigDecimal referencePrice) {
        log.info("[{}] executeBuy triggered at price={}", strategy.getSymbol(), currentPrice);
        AccountDto account = tradingService.getAccount().block(Duration.ofSeconds(6));
        if (account == null || account.cash() == null || account.cash().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[{}] SKIP executeBuy — no available cash (account={})", strategy.getSymbol(), account);
            record(strategy, OrderSide.BUY, null, currentPrice, referencePrice, StrategyExecutionStatus.SKIPPED, "No available cash");
            return;
        }

        BigDecimal cashToUse = account.cash().multiply(strategy.getBuyCashPercent().movePointLeft(2));
        BigDecimal qty = cashToUse.divide(currentPrice, 6, RoundingMode.DOWN);
        log.info("[{}] executeBuy: cash={} cashToUse={} qty={}", strategy.getSymbol(), account.cash(), cashToUse, qty);

        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[{}] SKIP executeBuy — computed qty is zero", strategy.getSymbol());
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
        touchStrategyAction(strategy, "BUY");
        record(strategy, OrderSide.BUY, order == null ? null : order.id(), currentPrice, referencePrice, StrategyExecutionStatus.SUCCESS, "BUY executed");
    }

    private void executeSell(
            StrategyConfigEntity strategy,
            PositionEntity position,
            BigDecimal currentPrice,
            BigDecimal referencePrice
    ) {
        if (position == null || position.getQty() == null || position.getQty().compareTo(BigDecimal.ZERO) <= 0) {
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
        touchStrategyAction(strategy, "SELL");
        record(strategy, OrderSide.SELL, order == null ? null : order.id(), currentPrice, referencePrice, StrategyExecutionStatus.SUCCESS, "SELL executed");
    }

    private boolean isModeAllowed(StrategyConfigEntity strategy) {
        String broker = strategy.getBroker() != null ? strategy.getBroker().toLowerCase() : "simulator";
        return broker.equalsIgnoreCase(mode);
    }

    private boolean isCooldownActive(StrategyConfigEntity strategy) {
        if (strategy.getLastActionAt() == null || strategy.getCooldownMinutes() == null || strategy.getCooldownMinutes() <= 0) {
            return false;
        }
        return strategy.getLastActionAt().plusMinutes(strategy.getCooldownMinutes()).isAfter(LocalDateTime.now());
    }

    /**
     * Side-aware cooldown: only fires if the last executed action was the SAME side.
     *   BUY -> SELL: allowed instantly (different side — profitable exit should never be blocked)
     *   SELL -> SELL: blocked by cooldown (prevents infinite repeat sells of partial positions)
     *   BUY -> BUY:  blocked by cooldown (prevents rapid repeat buying)
     *   SELL -> BUY: allowed instantly (different side)
     */
    private boolean isSideCooldownActive(StrategyConfigEntity strategy, String intendedSide) {
        if (!isCooldownActive(strategy)) return false;
        String lastSide = strategy.getLastActionSide();
        return intendedSide.equalsIgnoreCase(lastSide);
    }

    private long successCountToday(UUID strategyId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        // Count only BUY-side executions — SELL orders must never consume from the daily BUY limit.
        // Previously this counted all executions (BUY + SELL combined), which incorrectly blocked
        // subsequent BUYs after a SELL even if the BUY limit had not been reached.
        return strategyExecutionRepository.countByStrategyConfigIdAndSideAndStatusAndExecutedAtBetween(
                strategyId,
                OrderSide.BUY,
                StrategyExecutionStatus.SUCCESS,
                start,
                end
        );
    }

    private void touchStrategyAction(StrategyConfigEntity strategy, String side) {
        strategy.setLastActionAt(LocalDateTime.now());
        strategy.setLastActionSide(side);
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
        execution.setBroker(strategy.getBroker() != null ? strategy.getBroker() : "simulator");
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
