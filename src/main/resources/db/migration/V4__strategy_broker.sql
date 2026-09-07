-- Add broker column to strategy_configs (replaces simulatorEnabled / alpacaEnabled flags)
ALTER TABLE strategy_configs ADD COLUMN broker VARCHAR(50) NOT NULL DEFAULT 'simulator';

-- Backfill: if alpaca was enabled on existing strategies, keep alpaca; simulator otherwise
UPDATE strategy_configs SET broker = 'alpaca' WHERE alpaca_enabled = TRUE AND simulator_enabled = FALSE;

-- Add broker column to strategy_executions so the report knows which broker fired each trade
ALTER TABLE strategy_executions ADD COLUMN broker VARCHAR(50);

CREATE INDEX idx_strategy_configs_broker ON strategy_configs(broker);
