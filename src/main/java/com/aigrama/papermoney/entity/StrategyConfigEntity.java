package com.aigrama.papermoney.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-stock automation configuration.
 */
@Entity
@Table(name = "strategy_configs")
public class StrategyConfigEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(name = "buy_drop_percent", nullable = false, precision = 10, scale = 4)
    private BigDecimal buyDropPercent;

    @Column(name = "sell_rise_percent", nullable = false, precision = 10, scale = 4)
    private BigDecimal sellRisePercent;

    @Column(name = "buy_cash_percent", nullable = false, precision = 10, scale = 4)
    private BigDecimal buyCashPercent;

    @Column(name = "sell_position_percent", nullable = false, precision = 10, scale = 4)
    private BigDecimal sellPositionPercent;

    @Column(name = "max_orders_per_day", nullable = false)
    private Integer maxOrdersPerDay;

    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "broker", nullable = false, length = 50)
    private String broker;

    /** Market price captured at strategy creation time. Used as stable reference
     *  when no holding position exists, so triggers don't float with injected prices. */
    @Column(name = "baseline_price", precision = 19, scale = 6)
    private BigDecimal baselinePrice;

    @Column(name = "last_action_at")
    private LocalDateTime lastActionAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public BigDecimal getBuyDropPercent() {
        return buyDropPercent;
    }

    public void setBuyDropPercent(BigDecimal buyDropPercent) {
        this.buyDropPercent = buyDropPercent;
    }

    public BigDecimal getSellRisePercent() {
        return sellRisePercent;
    }

    public void setSellRisePercent(BigDecimal sellRisePercent) {
        this.sellRisePercent = sellRisePercent;
    }

    public BigDecimal getBuyCashPercent() {
        return buyCashPercent;
    }

    public void setBuyCashPercent(BigDecimal buyCashPercent) {
        this.buyCashPercent = buyCashPercent;
    }

    public BigDecimal getSellPositionPercent() {
        return sellPositionPercent;
    }

    public void setSellPositionPercent(BigDecimal sellPositionPercent) {
        this.sellPositionPercent = sellPositionPercent;
    }

    public Integer getMaxOrdersPerDay() {
        return maxOrdersPerDay;
    }

    public void setMaxOrdersPerDay(Integer maxOrdersPerDay) {
        this.maxOrdersPerDay = maxOrdersPerDay;
    }

    public Integer getCooldownMinutes() {
        return cooldownMinutes;
    }

    public void setCooldownMinutes(Integer cooldownMinutes) {
        this.cooldownMinutes = cooldownMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getBroker() {
        return broker;
    }

    public void setBroker(String broker) {
        this.broker = broker;
    }

    public BigDecimal getBaselinePrice() {
        return baselinePrice;
    }

    public void setBaselinePrice(BigDecimal baselinePrice) {
        this.baselinePrice = baselinePrice;
    }

    public LocalDateTime getLastActionAt() {
        return lastActionAt;
    }

    public void setLastActionAt(LocalDateTime lastActionAt) {
        this.lastActionAt = lastActionAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
