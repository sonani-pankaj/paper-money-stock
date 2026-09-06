package com.aigrama.papermoney.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted account settings state (mode, available cash).
 */
@Entity
@Table(name = "account_state")
public class AccountStateEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String mode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cash;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public AccountStateEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public void setCash(BigDecimal cash) {
        this.cash = cash;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
