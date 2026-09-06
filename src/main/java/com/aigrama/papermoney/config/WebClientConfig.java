package com.aigrama.papermoney.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Infrastructure configuration for outbound HTTP clients and cache.
 */
@Configuration
@EnableCaching
public class WebClientConfig {

    /**
     * WebClient for Alpaca trading REST API.
     */
    @Bean(name = "alpacaWebClient")
    public WebClient alpacaWebClient(
            @Value("${paperstock.alpaca.base-url}") String baseUrl,
            @Value("${paperstock.alpaca.api-key}") String apiKey,
            @Value("${paperstock.alpaca.api-secret}") String apiSecret
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("APCA-API-KEY-ID", apiKey)
                .defaultHeader("APCA-API-SECRET-KEY", apiSecret)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    /**
     * WebClient for free market data provider.
     */
    @Bean(name = "marketDataWebClient")
    public WebClient marketDataWebClient(@Value("${paperstock.market-data.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    /**
     * Caffeine cache manager for short-lived market data snapshots.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("marketData");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofSeconds(30)));
        return cacheManager;
    }
}
