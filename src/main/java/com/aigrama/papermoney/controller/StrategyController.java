package com.aigrama.papermoney.controller;

import com.aigrama.papermoney.dto.StrategyConfigDto;
import com.aigrama.papermoney.dto.StrategyChartPointDto;
import com.aigrama.papermoney.dto.StrategyChartSeriesDto;
import com.aigrama.papermoney.dto.StrategyConfigRequestDto;
import com.aigrama.papermoney.dto.StrategyExecutionDto;
import com.aigrama.papermoney.dto.StrategyTradeReportDto;
import com.aigrama.papermoney.entity.OrderSide;
import com.aigrama.papermoney.entity.MarketSnapshotEntity;
import com.aigrama.papermoney.entity.PositionEntity;
import com.aigrama.papermoney.entity.StrategyConfigEntity;
import com.aigrama.papermoney.entity.StrategyExecutionEntity;
import com.aigrama.papermoney.entity.StrategyExecutionStatus;
import com.aigrama.papermoney.entity.TradeEntity;
import com.aigrama.papermoney.repository.MarketSnapshotRepository;
import com.aigrama.papermoney.repository.PositionRepository;
import com.aigrama.papermoney.repository.StrategyConfigRepository;
import com.aigrama.papermoney.repository.StrategyExecutionRepository;
import com.aigrama.papermoney.repository.TradeRepository;
import com.aigrama.papermoney.service.StrategyConfigService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API endpoints for strategy config and activity monitoring.
 */
@RestController
@Validated
@RequestMapping("/api/strategies")
public class StrategyController {

    private final StrategyConfigService strategyConfigService;
    private final StrategyExecutionRepository strategyExecutionRepository;
    private final StrategyConfigRepository strategyConfigRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final long maxStaleSeconds;

    public StrategyController(
            StrategyConfigService strategyConfigService,
            StrategyExecutionRepository strategyExecutionRepository,
            StrategyConfigRepository strategyConfigRepository,
            MarketSnapshotRepository marketSnapshotRepository,
            PositionRepository positionRepository,
                TradeRepository tradeRepository,
            @Value("${paperstock.market-data.max-stale-seconds:120}") long maxStaleSeconds
    ) {
        this.strategyConfigService = strategyConfigService;
        this.strategyExecutionRepository = strategyExecutionRepository;
        this.strategyConfigRepository = strategyConfigRepository;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.positionRepository = positionRepository;
        this.tradeRepository = tradeRepository;
        this.maxStaleSeconds = maxStaleSeconds;
    }

    @PostMapping
    public StrategyConfigDto create(@Valid @RequestBody StrategyConfigRequestDto request) {
        return strategyConfigService.create(request);
    }

    @GetMapping
    public List<StrategyConfigDto> list() {
        return strategyConfigService.list();
    }

    @PutMapping("/{id}")
    public StrategyConfigDto update(@PathVariable("id") String id, @Valid @RequestBody StrategyConfigRequestDto request) {
        return strategyConfigService.update(id, request);
    }

    @PostMapping("/{id}/pause")
    public StrategyConfigDto pause(@PathVariable("id") String id) {
        return strategyConfigService.pause(id);
    }

    @PostMapping("/{id}/resume")
    public StrategyConfigDto resume(@PathVariable("id") String id) {
        return strategyConfigService.resume(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        strategyConfigService.delete(id);
    }

    @GetMapping("/activity")
    public List<StrategyExecutionDto> activity(@RequestParam(value = "symbol", required = false) String symbol) {
        List<StrategyExecutionEntity> executions = (symbol == null || symbol.isBlank())
                ? strategyExecutionRepository.findTop200ByOrderByExecutedAtDesc()
                : strategyExecutionRepository.findTop200BySymbolOrderByExecutedAtDesc(symbol.toUpperCase());
        return executions.stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}/executions")
    public List<StrategyExecutionDto> executions(@PathVariable("id") String strategyId) {
        return strategyExecutionRepository.findTop200ByStrategyConfigIdOrderByExecutedAtDesc(java.util.UUID.fromString(strategyId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/report")
    public List<StrategyTradeReportDto> report() {
        List<StrategyExecutionEntity> executions = strategyExecutionRepository.findByStatus(StrategyExecutionStatus.SUCCESS);
        ArrayList<UUID> orderIds = new ArrayList<>();
        for (StrategyExecutionEntity execution : executions) {
            if (execution.getOrderId() == null || execution.getOrderId().isBlank()) {
                continue;
            }
            try {
                orderIds.add(UUID.fromString(execution.getOrderId()));
            } catch (IllegalArgumentException ignored) {
                // Non-UUID order IDs can occur in non-simulator modes; skip those for trade-based aggregation.
            }
        }

        List<TradeEntity> trades = orderIds.isEmpty() ? List.of() : tradeRepository.findAllByOrder_IdIn(orderIds);
        Map<String, Totals> bySymbol = new LinkedHashMap<>();

        for (TradeEntity trade : trades) {
            String symbol = trade.getSymbol();
            Totals totals = bySymbol.computeIfAbsent(symbol, key -> new Totals());
            BigDecimal amount = trade.getQty().multiply(trade.getPrice());

            if (trade.getOrder() != null && trade.getOrder().getSide() == OrderSide.SELL) {
                totals.sellQty = totals.sellQty.add(trade.getQty());
                totals.sellAmount = totals.sellAmount.add(amount);
                totals.sellTrades++;
            } else {
                totals.buyQty = totals.buyQty.add(trade.getQty());
                totals.buyAmount = totals.buyAmount.add(amount);
                totals.buyTrades++;
            }
        }

        return bySymbol.entrySet().stream()
                .map(entry -> new StrategyTradeReportDto(
                        entry.getKey(),
                        entry.getValue().buyQty,
                        entry.getValue().buyAmount,
                        entry.getValue().buyTrades,
                        entry.getValue().sellQty,
                        entry.getValue().sellAmount,
                        entry.getValue().sellTrades
                ))
                .toList();
    }

    @GetMapping("/{id}/chart")
    public StrategyChartSeriesDto chart(
            @PathVariable("id") String strategyId,
            @RequestParam(value = "limit", defaultValue = "120") int limit
    ) {
        int boundedLimit = Math.max(10, Math.min(limit, 500));
        StrategyConfigEntity strategy = strategyConfigRepository.findById(java.util.UUID.fromString(strategyId))
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));

        BigDecimal referencePrice = positionRepository.findBySymbol(strategy.getSymbol())
                .map(PositionEntity::getAveragePrice)
                .orElse(BigDecimal.ZERO);

        BigDecimal buyTrigger = trigger(referencePrice, strategy.getBuyDropPercent(), true);
        BigDecimal sellTrigger = trigger(referencePrice, strategy.getSellRisePercent(), false);

        List<MarketSnapshotEntity> snapshots = marketSnapshotRepository.findBySymbolOrderByCapturedAtDesc(
                strategy.getSymbol(),
                PageRequest.of(0, boundedLimit)
        );

        List<StrategyChartPointDto> points = snapshots.stream()
                .map(s -> new StrategyChartPointDto(s.getCapturedAt(), s.getPrice(), buyTrigger, sellTrigger))
                .toList();

        LocalDateTime latestQuoteAt = snapshots.isEmpty() ? null : snapshots.get(0).getCapturedAt();
        Long latestAgeSeconds = latestQuoteAt == null
                ? null
                : Math.max(0, ChronoUnit.SECONDS.between(latestQuoteAt, LocalDateTime.now()));
        boolean stale = latestAgeSeconds != null && latestAgeSeconds > maxStaleSeconds;

        return new StrategyChartSeriesDto(
                strategy.getId().toString(),
                strategy.getSymbol(),
                referencePrice,
                strategy.getBuyDropPercent(),
                strategy.getSellRisePercent(),
                stale,
                latestAgeSeconds,
                latestQuoteAt,
                points
        );
    }

    private BigDecimal trigger(BigDecimal referencePrice, BigDecimal percent, boolean isBuy) {
        if (referencePrice == null || percent == null || referencePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal factor = isBuy
                ? BigDecimal.ONE.subtract(percent.movePointLeft(2))
                : BigDecimal.ONE.add(percent.movePointLeft(2));
        return referencePrice.multiply(factor).setScale(6, RoundingMode.HALF_UP);
    }

    private StrategyExecutionDto toDto(StrategyExecutionEntity execution) {
        return new StrategyExecutionDto(
                execution.getId().toString(),
                execution.getStrategyConfigId().toString(),
                execution.getSymbol(),
                execution.getSide().name().toLowerCase(),
                execution.getTriggerPrice(),
                execution.getReferencePrice(),
                execution.getOrderId(),
                execution.getStatus().name().toLowerCase(),
                execution.getMessage(),
                execution.getExecutedAt()
        );
    }

    private static class Totals {
        private BigDecimal buyQty = BigDecimal.ZERO;
        private BigDecimal buyAmount = BigDecimal.ZERO;
        private long buyTrades = 0;
        private BigDecimal sellQty = BigDecimal.ZERO;
        private BigDecimal sellAmount = BigDecimal.ZERO;
        private long sellTrades = 0;
    }
}
