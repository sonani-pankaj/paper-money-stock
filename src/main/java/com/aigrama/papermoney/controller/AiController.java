package com.aigrama.papermoney.controller;

import com.aigrama.papermoney.dto.AiDecisionDto;
import com.aigrama.papermoney.dto.BacktestComparisonDto;
import com.aigrama.papermoney.dto.BacktestRequestDto;
import com.aigrama.papermoney.dto.SentimentScoreDto;
import com.aigrama.papermoney.dto.SentimentSignalRequestDto;
import com.aigrama.papermoney.service.AiDecisionService;
import com.aigrama.papermoney.service.BacktestService;
import com.aigrama.papermoney.service.SentimentService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * AI endpoints for sentiment ingestion, decision preview, and backtesting.
 */
@RestController
@Validated
@RequestMapping("/api/ai")
public class AiController {

    private final SentimentService sentimentService;
    private final AiDecisionService aiDecisionService;
    private final BacktestService backtestService;

    public AiController(
            SentimentService sentimentService,
            AiDecisionService aiDecisionService,
            BacktestService backtestService
    ) {
        this.sentimentService = sentimentService;
        this.aiDecisionService = aiDecisionService;
        this.backtestService = backtestService;
    }

    @PostMapping("/sentiment/signals")
    public SentimentScoreDto ingestSentiment(@Valid @RequestBody SentimentSignalRequestDto request) {
        return sentimentService.ingest(request);
    }

    @GetMapping("/sentiment/{symbol}")
    public SentimentScoreDto score(@PathVariable("symbol") String symbol) {
        return sentimentService.currentScore(symbol);
    }

    @GetMapping("/decision")
    public AiDecisionDto decisionPreview(
            @RequestParam("symbol") String symbol,
            @RequestParam("buyDropPercent") BigDecimal buyDropPercent,
            @RequestParam("sellRisePercent") BigDecimal sellRisePercent,
            @RequestParam("currentPrice") BigDecimal currentPrice
    ) {
        return aiDecisionService.evaluate(symbol, buyDropPercent, sellRisePercent, currentPrice);
    }

    @PostMapping("/backtest")
    public BacktestComparisonDto backtest(@Valid @RequestBody BacktestRequestDto request) {
        return backtestService.compare(request);
    }
}