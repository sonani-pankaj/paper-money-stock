package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.MarketSnapshotDto;
import com.aigrama.papermoney.dto.StrategyConfigDto;
import com.aigrama.papermoney.dto.StrategyConfigRequestDto;
import com.aigrama.papermoney.entity.PositionEntity;
import com.aigrama.papermoney.entity.StrategyConfigEntity;
import com.aigrama.papermoney.repository.PositionRepository;
import com.aigrama.papermoney.repository.StrategyConfigRepository;
import com.aigrama.papermoney.repository.StrategyExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CRUD service for stock strategy configurations.
 */
@Service
public class StrategyConfigService {

    private final StrategyConfigRepository strategyConfigRepository;
    private final StrategyExecutionRepository strategyExecutionRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;

    public StrategyConfigService(
            StrategyConfigRepository strategyConfigRepository,
            StrategyExecutionRepository strategyExecutionRepository,
            PositionRepository positionRepository,
            MarketDataService marketDataService
    ) {
        this.strategyConfigRepository = strategyConfigRepository;
        this.strategyExecutionRepository = strategyExecutionRepository;
        this.positionRepository = positionRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public StrategyConfigDto create(StrategyConfigRequestDto request) {
        validateThresholds(request);
        String symbol = request.symbol().toUpperCase();
        strategyConfigRepository.findBySymbol(symbol).ifPresent(existing -> {
            throw new IllegalArgumentException("Strategy already exists for symbol: " + symbol);
        });

        StrategyConfigEntity entity = new StrategyConfigEntity();
        entity.setId(UUID.randomUUID());
        apply(entity, request);
        entity.setSymbol(symbol);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        // Capture baseline price at creation time — used as stable reference
        // when no position is held, so triggers don't float with live price changes
        try {
            MarketSnapshotDto snapshot = marketDataService.latestSnapshot(symbol).block();
            if (snapshot != null && snapshot.price() != null) {
                entity.setBaselinePrice(snapshot.price());
            }
        } catch (Exception ignored) {
            // Non-fatal: baseline price will be null and fall back to current price
        }

        return toDto(strategyConfigRepository.save(entity));

    }

    public List<StrategyConfigDto> list() {
        return strategyConfigRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public StrategyConfigDto update(String strategyId, StrategyConfigRequestDto request) {
        validateThresholds(request);
        StrategyConfigEntity entity = strategyConfigRepository.findById(UUID.fromString(strategyId))
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));

        String symbol = request.symbol().toUpperCase();
        strategyConfigRepository.findBySymbol(symbol)
                .filter(found -> !found.getId().equals(entity.getId()))
                .ifPresent(found -> {
                    throw new IllegalArgumentException("Strategy already exists for symbol: " + symbol);
                });

        apply(entity, request);
        entity.setSymbol(symbol);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(strategyConfigRepository.save(entity));
    }

    @Transactional
    public StrategyConfigDto pause(String strategyId) {
        StrategyConfigEntity entity = strategyConfigRepository.findById(UUID.fromString(strategyId))
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));
        entity.setActive(false);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(strategyConfigRepository.save(entity));
    }

    @Transactional
    public StrategyConfigDto resume(String strategyId) {
        StrategyConfigEntity entity = strategyConfigRepository.findById(UUID.fromString(strategyId))
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));
        entity.setActive(true);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(strategyConfigRepository.save(entity));
    }

    @Transactional
    public void delete(String strategyId) {
        UUID id = UUID.fromString(strategyId);
        StrategyConfigEntity entity = strategyConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));
        strategyExecutionRepository.deleteByStrategyConfigId(entity.getId());
        strategyConfigRepository.delete(entity);
    }

    private void validateThresholds(StrategyConfigRequestDto request) {
        if (request.buyDropPercent().compareTo(BigDecimal.ZERO) <= 0
                || request.sellRisePercent().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Buy/Sell threshold percentages must be positive");
        }
    }

    private void apply(StrategyConfigEntity entity, StrategyConfigRequestDto request) {
        entity.setBuyDropPercent(request.buyDropPercent());
        entity.setSellRisePercent(request.sellRisePercent());
        entity.setBuyCashPercent(request.buyCashPercent());
        entity.setSellPositionPercent(request.sellPositionPercent());
        entity.setMaxOrdersPerDay(request.maxOrdersPerDay());
        entity.setCooldownMinutes(request.cooldownMinutes());
        entity.setActive(Boolean.TRUE.equals(request.active()));
        entity.setBroker(request.broker() != null ? request.broker().toLowerCase() : "simulator");
    }

    private StrategyConfigDto toDto(StrategyConfigEntity entity) {
        BigDecimal currentPrice = null;
        try {
            MarketSnapshotDto snapshot = marketDataService.latestSnapshot(entity.getSymbol()).block();
            if (snapshot != null) {
                currentPrice = snapshot.price();
            }
        } catch (Exception ignored) {
        }

        PositionEntity position = positionRepository.findBySymbol(entity.getSymbol()).orElse(null);
        BigDecimal referencePrice = (position != null && position.getAveragePrice() != null && position.getAveragePrice().compareTo(BigDecimal.ZERO) > 0)
                ? position.getAveragePrice()
                : currentPrice;

        BigDecimal buyTriggerPrice = null;
        BigDecimal sellTriggerPrice = null;
        if (referencePrice != null) {
            buyTriggerPrice = referencePrice.multiply(BigDecimal.ONE.subtract(entity.getBuyDropPercent().movePointLeft(2)))
                    .setScale(4, RoundingMode.HALF_UP);
            sellTriggerPrice = referencePrice.multiply(BigDecimal.ONE.add(entity.getSellRisePercent().movePointLeft(2)))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        return new StrategyConfigDto(
                entity.getId().toString(),
                entity.getSymbol(),
                entity.getBuyDropPercent(),
                entity.getSellRisePercent(),
                entity.getBuyCashPercent(),
                entity.getSellPositionPercent(),
                entity.getMaxOrdersPerDay(),
                entity.getCooldownMinutes(),
                entity.isActive(),
                entity.getBroker() != null ? entity.getBroker() : "simulator",
                entity.getLastActionAt(),
                currentPrice,
                referencePrice,
                buyTriggerPrice,
                sellTriggerPrice
        );
    }
}
