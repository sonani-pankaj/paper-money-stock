package com.aigrama.papermoney.repository;

import com.aigrama.papermoney.entity.StrategyExecutionEntity;
import com.aigrama.papermoney.entity.StrategyExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data access for strategy execution audit records.
 */
public interface StrategyExecutionRepository extends JpaRepository<StrategyExecutionEntity, UUID> {
    long countByStrategyConfigIdAndStatusAndExecutedAtBetween(
            UUID strategyConfigId,
            StrategyExecutionStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    List<StrategyExecutionEntity> findTop200ByOrderByExecutedAtDesc();

    List<StrategyExecutionEntity> findTop200BySymbolOrderByExecutedAtDesc(String symbol);

    List<StrategyExecutionEntity> findTop200ByStrategyConfigIdOrderByExecutedAtDesc(UUID strategyConfigId);

    void deleteByStrategyConfigId(UUID strategyConfigId);
}
