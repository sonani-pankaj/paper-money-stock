package com.aigrama.papermoney.repository;

import com.aigrama.papermoney.entity.StrategyConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for stock strategy configurations.
 */
public interface StrategyConfigRepository extends JpaRepository<StrategyConfigEntity, UUID> {
    List<StrategyConfigEntity> findAllByActiveTrue();

    Optional<StrategyConfigEntity> findBySymbol(String symbol);

    List<StrategyConfigEntity> findAllBySymbol(String symbol);
}
