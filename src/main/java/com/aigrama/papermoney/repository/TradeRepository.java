package com.aigrama.papermoney.repository;

import com.aigrama.papermoney.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Data access for trades.
 */
public interface TradeRepository extends JpaRepository<TradeEntity, UUID> {
	List<TradeEntity> findAllByOrder_IdIn(Collection<UUID> orderIds);
}
