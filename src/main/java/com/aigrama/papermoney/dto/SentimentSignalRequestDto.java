package com.aigrama.papermoney.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound sentiment signal from news/social text.
 */
public record SentimentSignalRequestDto(
        @NotBlank String symbol,
        @NotBlank String source,
        @NotBlank String content
) {
}