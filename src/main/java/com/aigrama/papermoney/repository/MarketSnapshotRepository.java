package com.aigrama.papermoney.repository;

import com.aigrama.papermoney.entity.MarketSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for market snapshots.
 */
public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshotEntity, UUID> {
    Optional<MarketSnapshotEntity> findTopBySymbolOrderByCapturedAtDesc(String symbol);
}
