package com.aigrama.papermoney.repository;

import com.aigrama.papermoney.entity.SentimentSignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data access for sentiment signals.
 */
public interface SentimentSignalRepository extends JpaRepository<SentimentSignalEntity, UUID> {
    List<SentimentSignalEntity> findBySymbolAndCapturedAtAfterOrderByCapturedAtDesc(String symbol, LocalDateTime capturedAt);
}