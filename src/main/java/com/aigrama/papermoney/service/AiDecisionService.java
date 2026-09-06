package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.AiDecisionDto;
import com.aigrama.papermoney.dto.SentimentScoreDto;
import com.aigrama.papermoney.entity.MarketSnapshotEntity;
import com.aigrama.papermoney.repository.MarketSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Computes AI signals used to adapt thresholds and gate trades.
 */
@Service
public class AiDecisionService {

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final SentimentService sentimentService;
    private final int volatilityWindow;
    private final BigDecimal volatilityFactor;
    private final BigDecimal probabilityThreshold;

    public AiDecisionService(
            MarketSnapshotRepository marketSnapshotRepository,
            SentimentService sentimentService,
            @Value("${paperstock.ai.volatility.window:30}") int volatilityWindow,
            @Value("${paperstock.ai.volatility.factor:2.0}") BigDecimal volatilityFactor,
            @Value("${paperstock.ai.probability.threshold:0.55}") BigDecimal probabilityThreshold
    ) {
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.sentimentService = sentimentService;
        this.volatilityWindow = volatilityWindow;
        this.volatilityFactor = volatilityFactor;
        this.probabilityThreshold = probabilityThreshold;
    }

    public AiDecisionDto evaluate(String symbol, BigDecimal baseBuyDropPercent, BigDecimal baseSellRisePercent, BigDecimal currentPrice) {
        BigDecimal volatilityPct = estimateVolatilityPct(symbol);
        SentimentScoreDto sentiment = sentimentService.currentScore(symbol);

        BigDecimal multiplier = BigDecimal.ONE.add(volatilityPct.multiply(volatilityFactor));
        BigDecimal dynamicBuy = baseBuyDropPercent.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
        BigDecimal dynamicSell = baseSellRisePercent.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);

        BigDecimal sentimentScore = sentiment.score();
        BigDecimal momentum = estimateMomentumPct(symbol, currentPrice);

        BigDecimal buyProbability = sigmoid(
                BigDecimal.valueOf(-0.15)
                        .add(sentimentScore.multiply(BigDecimal.valueOf(0.9)))
                        .add(momentum.multiply(BigDecimal.valueOf(1.3)))
                        .subtract(volatilityPct.multiply(BigDecimal.valueOf(0.8)))
        );

        BigDecimal sellProbability = sigmoid(
                BigDecimal.valueOf(-0.15)
                        .subtract(sentimentScore.multiply(BigDecimal.valueOf(0.9)))
                        .subtract(momentum.multiply(BigDecimal.valueOf(1.1)))
                        .subtract(volatilityPct.multiply(BigDecimal.valueOf(0.6)))
        );

        return new AiDecisionDto(
                sentimentScore,
                volatilityPct,
                dynamicBuy,
                dynamicSell,
                buyProbability,
                sellProbability,
                buyProbability.compareTo(probabilityThreshold) >= 0,
                sellProbability.compareTo(probabilityThreshold) >= 0
        );
    }

    private BigDecimal estimateVolatilityPct(String symbol) {
        List<MarketSnapshotEntity> snapshots = marketSnapshotRepository.findBySymbolOrderByCapturedAtDesc(
                symbol.toUpperCase(),
                PageRequest.of(0, Math.max(5, volatilityWindow))
        );
        if (snapshots.size() < 2) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }

        BigDecimal meanAbsReturn = BigDecimal.ZERO;
        int n = 0;
        for (int i = 0; i < snapshots.size() - 1; i++) {
            BigDecimal now = snapshots.get(i).getPrice();
            BigDecimal prev = snapshots.get(i + 1).getPrice();
            if (now == null || prev == null || prev.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal r = now.subtract(prev)
                    .divide(prev, 8, RoundingMode.HALF_UP)
                    .abs();
            meanAbsReturn = meanAbsReturn.add(r);
            n++;
        }
        if (n == 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return meanAbsReturn.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateMomentumPct(String symbol, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        List<MarketSnapshotEntity> snapshots = marketSnapshotRepository.findBySymbolOrderByCapturedAtDesc(
                symbol.toUpperCase(),
                PageRequest.of(0, 8)
        );
        if (snapshots.size() < 2 || snapshots.get(snapshots.size() - 1).getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal oldest = snapshots.get(snapshots.size() - 1).getPrice();
        return currentPrice.subtract(oldest).divide(oldest, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal sigmoid(BigDecimal x) {
        double v = x.doubleValue();
        double s = 1.0d / (1.0d + Math.exp(-v));
        return BigDecimal.valueOf(s).setScale(6, RoundingMode.HALF_UP);
    }
}