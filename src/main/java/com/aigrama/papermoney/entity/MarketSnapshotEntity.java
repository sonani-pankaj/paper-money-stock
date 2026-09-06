package com.aigrama.papermoney.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Latest observed market price for a symbol.
 */
@Entity
@Table(name = "market_snapshots")
public class MarketSnapshotEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }
}
