-- Stable baseline reference price captured at strategy creation time.
-- Used as the reference when no holding position exists, so buy/sell
-- triggers don't float when a simulator price override is injected.
ALTER TABLE strategy_configs ADD COLUMN baseline_price NUMERIC(19, 6);
