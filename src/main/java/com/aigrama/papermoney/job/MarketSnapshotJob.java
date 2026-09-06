package com.aigrama.papermoney.job;

import com.aigrama.papermoney.entity.PositionEntity;
import com.aigrama.papermoney.entity.StrategyConfigEntity;
import com.aigrama.papermoney.repository.PositionRepository;
import com.aigrama.papermoney.repository.StrategyConfigRepository;
import com.aigrama.papermoney.service.MarketDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scheduled job that refreshes snapshots for active strategy and holding symbols.
 */
@Component
public class MarketSnapshotJob {

    private final MarketDataService marketDataService;
    private final StrategyConfigRepository strategyConfigRepository;
    private final PositionRepository positionRepository;
    private final List<String> defaultSymbols;

    public MarketSnapshotJob(
            MarketDataService marketDataService,
            StrategyConfigRepository strategyConfigRepository,
            PositionRepository positionRepository,
            @Value("${paperstock.symbols:}") String symbols
    ) {
        this.marketDataService = marketDataService;
        this.strategyConfigRepository = strategyConfigRepository;
        this.positionRepository = positionRepository;
        this.defaultSymbols = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .toList();
    }

    @Scheduled(fixedDelayString = "${paperstock.market-data.poll-ms:30000}")
    public void refresh() {
        Set<String> targetSymbols = new HashSet<>();

        strategyConfigRepository.findAllByActiveTrue().stream()
                .map(StrategyConfigEntity::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .map(String::toUpperCase)
                .forEach(targetSymbols::add);

        positionRepository.findAll().stream()
                .map(PositionEntity::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .map(String::toUpperCase)
                .forEach(targetSymbols::add);

        if (targetSymbols.isEmpty()) {
            targetSymbols.addAll(defaultSymbols);
        }

        targetSymbols.forEach(symbol -> marketDataService.refreshAndStore(symbol).subscribe());
    }
}
