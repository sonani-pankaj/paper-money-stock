package com.aigrama.papermoney.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry that tracks manually overridden prices in Simulator mode.
 *
 * When the user injects a price via SimulatorController, the symbol is pinned
 * for a configurable duration. While pinned, MarketSnapshotJob will skip that
 * symbol so the real market poll does not overwrite the injected test price.
 */
@Component
public class SimulatorPriceRegistry {

    /** How long an override pin stays active after the last injection. */
    private static final long PIN_DURATION_SECONDS = 300; // 5 minutes

    private final ConcurrentHashMap<String, Instant> pinnedUntil = new ConcurrentHashMap<>();

    /**
     * Pins a symbol for PIN_DURATION_SECONDS. Call this after injecting a price.
     */
    public void pin(String symbol) {
        pinnedUntil.put(symbol.toUpperCase(), Instant.now().plusSeconds(PIN_DURATION_SECONDS));
    }

    /**
     * Returns true if the symbol is currently pinned (i.e. the polling job should skip it).
     */
    public boolean isPinned(String symbol) {
        Instant until = pinnedUntil.get(symbol.toUpperCase());
        return until != null && until.isAfter(Instant.now());
    }

    /**
     * Explicitly releases a symbol pin (e.g. when the user resets the baseline).
     */
    public void unpin(String symbol) {
        pinnedUntil.remove(symbol.toUpperCase());
    }
}
