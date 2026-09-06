package com.aigrama.papermoney.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One sentiment signal item from news or social content.
 */
@Entity
@Table(name = "sentiment_signals")
public class SentimentSignalEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false, length = 2048)
    private String content;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal score;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }
}