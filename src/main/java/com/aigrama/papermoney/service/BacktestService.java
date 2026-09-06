package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.AiDecisionDto;
import com.aigrama.papermoney.dto.BacktestComparisonDto;
import com.aigrama.papermoney.dto.BacktestRequestDto;
import com.aigrama.papermoney.dto.BacktestResultDto;
import com.aigrama.papermoney.entity.MarketSnapshotEntity;
import com.aigrama.papermoney.repository.MarketSnapshotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight historical simulation for comparing AI-enabled vs rule-only behavior.
 */
@Service
public class BacktestService {

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final AiDecisionService aiDecisionService;

    public BacktestService(MarketSnapshotRepository marketSnapshotRepository, AiDecisionService aiDecisionService) {
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.aiDecisionService = aiDecisionService;
    }

    public BacktestComparisonDto compare(BacktestRequestDto request) {
        int limit = request.maxPoints() == null ? 500 : Math.max(20, Math.min(request.maxPoints(), 2000));
        List<MarketSnapshotEntity> desc = marketSnapshotRepository.findBySymbolOrderByCapturedAtDesc(
                request.symbol().toUpperCase(),
                PageRequest.of(0, limit)
        );
        if (desc.size() < 2) {
            throw new IllegalArgumentException("Not enough market snapshots for backtest. Need at least 2 points.");
        }

        ArrayList<MarketSnapshotEntity> points = new ArrayList<>(desc);
        points.sort((a, b) -> a.getCapturedAt().compareTo(b.getCapturedAt()));

        BacktestResultDto ruleOnly = run(request, points, false);
        BacktestResultDto aiEnabled = run(request, points, true);
        return new BacktestComparisonDto(request.symbol().toUpperCase(), aiEnabled, ruleOnly);
    }

    private BacktestResultDto run(BacktestRequestDto req, List<MarketSnapshotEntity> points, boolean aiMode) {
        BigDecimal cash = req.initialCash();
        BigDecimal qty = BigDecimal.ZERO;
        int buyTrades = 0;
        int sellTrades = 0;

        BigDecimal reference = points.get(0).getPrice();
        if (reference == null || reference.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid starting reference price for symbol.");
        }

        for (MarketSnapshotEntity point : points) {
            BigDecimal price = point.getPrice();
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal buyDrop = req.buyDropPercent();
            BigDecimal sellRise = req.sellRisePercent();
            boolean allowBuy = true;
            boolean allowSell = true;

            if (aiMode) {
                AiDecisionDto decision = aiDecisionService.evaluate(req.symbol(), req.buyDropPercent(), req.sellRisePercent(), price);
                buyDrop = decision.dynamicBuyDropPercent();
                sellRise = decision.dynamicSellRisePercent();
                allowBuy = decision.allowBuy();
                allowSell = decision.allowSell();
            }

            BigDecimal buyTrigger = reference.multiply(BigDecimal.ONE.subtract(buyDrop.movePointLeft(2)));
            BigDecimal sellTrigger = reference.multiply(BigDecimal.ONE.add(sellRise.movePointLeft(2)));

            if (allowBuy && price.compareTo(buyTrigger) <= 0 && cash.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal spend = cash.multiply(req.buyCashPercent().movePointLeft(2));
                BigDecimal bought = spend.divide(price, 6, RoundingMode.DOWN);
                if (bought.compareTo(BigDecimal.ZERO) > 0) {
                    cash = cash.subtract(bought.multiply(price));
                    qty = qty.add(bought);
                    buyTrades++;
                    reference = price;
                    continue;
                }
            }

            if (allowSell && price.compareTo(sellTrigger) >= 0 && qty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal sold = qty.multiply(req.sellPositionPercent().movePointLeft(2)).setScale(6, RoundingMode.DOWN);
                if (sold.compareTo(BigDecimal.ZERO) > 0) {
                    cash = cash.add(sold.multiply(price));
                    qty = qty.subtract(sold);
                    sellTrades++;
                    reference = price;
                }
            }
        }

        BigDecimal lastPrice = points.get(points.size() - 1).getPrice();
        BigDecimal endingValue = cash.add(qty.multiply(lastPrice));
        BigDecimal pnl = endingValue.subtract(req.initialCash());

        return new BacktestResultDto(
                aiMode ? "ai-enabled" : "rule-only",
                buyTrades,
                sellTrades,
                cash.setScale(2, RoundingMode.HALF_UP),
                qty.setScale(6, RoundingMode.HALF_UP),
                endingValue.setScale(2, RoundingMode.HALF_UP),
                pnl.setScale(2, RoundingMode.HALF_UP)
        );
    }
}