package com.aigrama.papermoney.repository;

import com.aigrama.papermoney.entity.AccountStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for account state entity.
 */
@Repository
public interface AccountStateRepository extends JpaRepository<AccountStateEntity, String> {
}
