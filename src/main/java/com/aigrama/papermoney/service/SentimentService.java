package com.aigrama.papermoney.service;

import com.aigrama.papermoney.dto.SentimentScoreDto;
import com.aigrama.papermoney.dto.SentimentSignalRequestDto;
import com.aigrama.papermoney.entity.SentimentSignalEntity;
import com.aigrama.papermoney.repository.SentimentSignalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Stores and aggregates sentiment signals from news/social content.
 */
@Service
public class SentimentService {

    private static final Set<String> POSITIVE = Set.of(
            "beat", "growth", "profit", "bullish", "upgrade", "surge", "strong", "record", "outperform", "buy"
    );
    private static final Set<String> NEGATIVE = Set.of(
            "miss", "loss", "bearish", "downgrade", "drop", "weak", "lawsuit", "risk", "sell", "decline"
    );

    private final SentimentSignalRepository sentimentSignalRepository;
    private final int lookbackHours;

    public SentimentService(
            SentimentSignalRepository sentimentSignalRepository,
            @Value("${paperstock.ai.sentiment.lookback-hours:24}") int lookbackHours
    ) {
        this.sentimentSignalRepository = sentimentSignalRepository;
        this.lookbackHours = lookbackHours;
    }

    public SentimentScoreDto ingest(SentimentSignalRequestDto request) {
        String symbol = request.symbol().trim().toUpperCase();
        BigDecimal score = lexiconScore(request.content());

        SentimentSignalEntity entity = new SentimentSignalEntity();
        entity.setId(UUID.randomUUID());
        entity.setSymbol(symbol);
        entity.setSource(request.source().trim().toLowerCase());
        entity.setContent(request.content().trim());
        entity.setScore(score);
        entity.setCapturedAt(LocalDateTime.now());
        sentimentSignalRepository.save(entity);

        return currentScore(symbol);
    }

    public SentimentScoreDto currentScore(String symbol) {
        String normalized = symbol.trim().toUpperCase();
        LocalDateTime from = LocalDateTime.now().minusHours(Math.max(1, lookbackHours));
        List<SentimentSignalEntity> signals = sentimentSignalRepository.findBySymbolAndCapturedAtAfterOrderByCapturedAtDesc(normalized, from);
        if (signals.isEmpty()) {
            return new SentimentScoreDto(normalized, BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP), 0);
        }

        BigDecimal sum = signals.stream().map(SentimentSignalEntity::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(signals.size()), 6, RoundingMode.HALF_UP);
        return new SentimentScoreDto(normalized, avg, signals.size());
    }

    private BigDecimal lexiconScore(String text) {
        String[] tokens = text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").split("\\s+");
        int pos = 0;
        int neg = 0;
        for (String token : tokens) {
            if (POSITIVE.contains(token)) {
                pos++;
            }
            if (NEGATIVE.contains(token)) {
                neg++;
            }
        }

        int total = pos + neg;
        if (total == 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(pos - neg)
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
    }
}