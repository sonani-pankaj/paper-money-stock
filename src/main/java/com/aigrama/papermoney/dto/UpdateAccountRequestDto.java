package com.aigrama.papermoney.dto;

import java.math.BigDecimal;

/**
 * Inbound payload for updating account settings (mode, cash).
 */
public record UpdateAccountRequestDto(
        String mode,
        BigDecimal cash
) {
}
