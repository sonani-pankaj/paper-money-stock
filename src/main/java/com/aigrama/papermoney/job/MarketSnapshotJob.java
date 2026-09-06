package com.aigrama.papermoney.job;

import com.aigrama.papermoney.service.MarketDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Scheduled job that refreshes snapshots for configured symbols.
 */
@Component
public class MarketSnapshotJob {

    private final MarketDataService marketDataService;
    private final List<String> symbols;

    public MarketSnapshotJob(
            MarketDataService marketDataService,
            @Value("${paperstock.symbols:AAPL,TSLA}") String symbols
    ) {
        this.marketDataService = marketDataService;
        this.symbols = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    @Scheduled(fixedDelayString = "${paperstock.market-data.poll-ms:30000}")
    public void refresh() {
        symbols.forEach(symbol -> marketDataService.refreshAndStore(symbol).subscribe());
    }
}
