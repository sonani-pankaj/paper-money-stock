package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Outbound account summary model.
 */
public record AccountDto(
        String mode,
        BigDecimal cash,
        BigDecimal equity
) {
}
