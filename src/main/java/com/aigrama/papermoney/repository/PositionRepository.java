package com.aigrama.papermoney.repository;

import com.aigrama.papermoney.entity.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for positions.
 */
public interface PositionRepository extends JpaRepository<PositionEntity, UUID> {
    Optional<PositionEntity> findBySymbol(String symbol);
}
