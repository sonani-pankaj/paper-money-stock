package com.aigrama.papermoney.repository;

import com.aigrama.papermoney.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for orders.
 */
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByExternalId(String externalId);
}
