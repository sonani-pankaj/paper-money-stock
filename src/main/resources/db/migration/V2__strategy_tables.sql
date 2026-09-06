CREATE TABLE strategy_configs (
    id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL UNIQUE,
    buy_drop_percent NUMERIC(10, 4) NOT NULL,
    sell_rise_percent NUMERIC(10, 4) NOT NULL,
    buy_cash_percent NUMERIC(10, 4) NOT NULL,
    sell_position_percent NUMERIC(10, 4) NOT NULL,
    max_orders_per_day INTEGER NOT NULL,
    cooldown_minutes INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    simulator_enabled BOOLEAN NOT NULL,
    alpaca_enabled BOOLEAN NOT NULL,
    last_action_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE strategy_executions (
    id UUID PRIMARY KEY,
    strategy_config_id UUID NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    trigger_price NUMERIC(19, 6),
    reference_price NUMERIC(19, 6),
    order_id VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    message VARCHAR(512),
    executed_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_strategy_execution_config FOREIGN KEY (strategy_config_id) REFERENCES strategy_configs(id)
);

CREATE INDEX idx_strategy_configs_active ON strategy_configs(active);
CREATE INDEX idx_strategy_executions_symbol_time ON strategy_executions(symbol, executed_at DESC);
CREATE INDEX idx_strategy_executions_config_time ON strategy_executions(strategy_config_id, executed_at DESC);
