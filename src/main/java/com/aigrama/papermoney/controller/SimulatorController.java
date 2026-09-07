package com.aigrama.papermoney.controller;

import com.aigrama.papermoney.entity.MarketSnapshotEntity;
import com.aigrama.papermoney.entity.PositionEntity;
import com.aigrama.papermoney.entity.StrategyConfigEntity;
import com.aigrama.papermoney.repository.MarketSnapshotRepository;
import com.aigrama.papermoney.repository.PositionRepository;
import com.aigrama.papermoney.repository.StrategyConfigRepository;
import com.aigrama.papermoney.service.MarketDataService;
import com.aigrama.papermoney.service.SimulatorPriceRegistry;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Simulator-only endpoints for manual price injection and testing.
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final StrategyConfigRepository strategyConfigRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final SimulatorPriceRegistry simulatorPriceRegistry;
    private final String mode;

    public SimulatorController(
            MarketSnapshotRepository marketSnapshotRepository,
            StrategyConfigRepository strategyConfigRepository,
            PositionRepository positionRepository,
            MarketDataService marketDataService,
            SimulatorPriceRegistry simulatorPriceRegistry,
            @Value("${paperstock.mode:simulator}") String mode
    ) {
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.strategyConfigRepository = strategyConfigRepository;
        this.positionRepository = positionRepository;
        this.marketDataService = marketDataService;
        this.simulatorPriceRegistry = simulatorPriceRegistry;
        this.mode = mode;
    }

    /**
     * Returns buy/sell trigger prices for a symbol's active strategies.
     * Used by the UI to show the user exactly what price to inject.
     */
    @GetMapping("/triggers")
    public StrategyTriggersResponse triggers(@RequestParam("symbol") String symbol) {
        guardSimulatorMode();

        String sym = symbol.trim().toUpperCase();
        List<StrategyConfigEntity> strategies = strategyConfigRepository.findAllBySymbol(sym);
        if (strategies.isEmpty()) {
            return new StrategyTriggersResponse(sym, null, null, null);
        }

        // Use the first matching strategy
        StrategyConfigEntity s = strategies.get(0);

        // Mirror the evaluator's reference price priority EXACTLY so hints always match reality:
        //   1) position average buy price (if holding shares)
        //   2) strategy baseline price (locked at creation or by Reset Baseline)
        //   3) current market snapshot price
        PositionEntity position = positionRepository.findBySymbol(sym).orElse(null);
        BigDecimal refPrice = null;
        if (position != null && position.getAveragePrice() != null
                && position.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
            refPrice = position.getAveragePrice();
        } else if (s.getBaselinePrice() != null && s.getBaselinePrice().compareTo(BigDecimal.ZERO) > 0) {
            refPrice = s.getBaselinePrice();
        } else {
            var snapshot = marketSnapshotRepository.findTopBySymbolOrderByCapturedAtDesc(sym);
            refPrice = snapshot.map(MarketSnapshotEntity::getPrice).orElse(null);
        }

        if (refPrice == null) {
            return new StrategyTriggersResponse(sym, null, null, null);
        }

        BigDecimal buyTrigger = s.getBuyDropPercent() == null ? null :
                refPrice.multiply(BigDecimal.ONE.subtract(s.getBuyDropPercent().movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellTrigger = s.getSellRisePercent() == null ? null :
                refPrice.multiply(BigDecimal.ONE.add(s.getSellRisePercent().movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP);

        return new StrategyTriggersResponse(sym, refPrice, buyTrigger, sellTrigger);
    }

    /**
     * Injects a manual price for a symbol into the market snapshot store.
     * Only available in simulator mode. The strategy engine reads the latest
     * snapshot on its next evaluation cycle.
     *
     * Baseline seeding rule: uses the real market price ALREADY in the DB
     * (before overwriting) — NOT the injected price — so the reference anchor
     * stays at the real market level and isn't polluted by test injections.
     */
    @PutMapping("/price")
    public PriceOverrideResponse overridePrice(@RequestBody PriceOverrideRequest request) {
        guardSimulatorMode();

        String symbol = request.symbol().trim().toUpperCase();
        if (symbol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Symbol is required");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be positive");
        }

        // Read the existing snapshot price BEFORE overwriting — this is the real market anchor
        BigDecimal realMarketPrice = marketSnapshotRepository
                .findTopBySymbolOrderByCapturedAtDesc(symbol)
                .map(MarketSnapshotEntity::getPrice)
                .orElse(null);

        // If no stored price at all, try fetching from market data service
        if (realMarketPrice == null) {
            try {
                var snap = marketDataService.refreshAndStore(symbol).block();
                if (snap != null) realMarketPrice = snap.price();
            } catch (Exception ignored) {}
        }

        // Upsert: update existing snapshot for this symbol or create a new one
        MarketSnapshotEntity snapshot = marketSnapshotRepository
                .findTopBySymbolOrderByCapturedAtDesc(symbol)
                .orElseGet(() -> {
                    MarketSnapshotEntity fresh = new MarketSnapshotEntity();
                    fresh.setId(UUID.randomUUID());
                    fresh.setSymbol(symbol);
                    return fresh;
                });

        snapshot.setPrice(request.price());
        snapshot.setCapturedAt(LocalDateTime.now());
        marketSnapshotRepository.save(snapshot);

        // Back-fill baselinePrice for strategies that predate V5 migration.
        // IMPORTANT: use the real market price as baseline, NOT the injected price.
        final BigDecimal baseline = realMarketPrice;
        if (baseline != null && baseline.compareTo(BigDecimal.ZERO) > 0) {
            List<StrategyConfigEntity> strategies = strategyConfigRepository.findAllBySymbol(symbol);
            for (StrategyConfigEntity s : strategies) {
                if (s.getBaselinePrice() == null) {
                    s.setBaselinePrice(baseline);
                    strategyConfigRepository.save(s);
                }
            }
        }

        // Pin this symbol: the market-data polling job will skip it for 5 minutes
        // so the real market API doesn't overwrite the injected price.
        simulatorPriceRegistry.pin(symbol);

        return new PriceOverrideResponse(symbol, request.price(), snapshot.getCapturedAt());
    }

    /**
     * Explicitly resets the baseline (reference) price for all strategies on a symbol.
     * Use this after buying shares at a new price to re-anchor the sell trigger.
     */
    @PutMapping("/baseline")
    public BaselineResponse setBaseline(@RequestBody PriceOverrideRequest request) {
        guardSimulatorMode();

        String symbol = request.symbol().trim().toUpperCase();
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be positive");
        }

        List<StrategyConfigEntity> strategies = strategyConfigRepository.findAllBySymbol(symbol);
        if (strategies.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No strategies found for: " + symbol);
        }

        for (StrategyConfigEntity s : strategies) {
            s.setBaselinePrice(request.price());
            strategyConfigRepository.save(s);
        }

        // Also reset the position's average price to the new baseline.
        // CRITICAL: the evaluator prioritises position.averagePrice over strategy.baselinePrice.
        // Without this step, the evaluator ignores the reset while shares are still held,
        // causing trigger hints to disagree with what actually fires.
        positionRepository.findBySymbol(symbol).ifPresent(pos -> {
            pos.setAveragePrice(request.price());
            positionRepository.save(pos);
        });

        // Unpin so the polling job can resume live prices after baseline reset
        simulatorPriceRegistry.unpin(symbol);

        return new BaselineResponse(symbol, request.price(), strategies.size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void guardSimulatorMode() {
        if (!"simulator".equalsIgnoreCase(mode)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This endpoint is only available in Simulator mode. Current mode: " + mode
            );
        }
    }

    // ── inner record types ────────────────────────────────────────────────────

    public record PriceOverrideRequest(
            @NotBlank String symbol,
            @NotNull @DecimalMin("0.000001") BigDecimal price
    ) {}

    public record PriceOverrideResponse(
            String symbol,
            BigDecimal price,
            LocalDateTime effectiveAt
    ) {}

    public record BaselineResponse(
            String symbol,
            BigDecimal baselinePrice,
            int strategiesUpdated
    ) {}

    public record StrategyTriggersResponse(
            String symbol,
            BigDecimal referencePrice,
            BigDecimal buyTrigger,
            BigDecimal sellTrigger
    ) {}
}
