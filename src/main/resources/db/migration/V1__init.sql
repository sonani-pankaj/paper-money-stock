CREATE TABLE orders (
    id UUID PRIMARY KEY,
    external_id VARCHAR(128),
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    qty NUMERIC(19, 6) NOT NULL,
    filled_qty NUMERIC(19, 6) NOT NULL,
    limit_price NUMERIC(19, 6),
    average_fill_price NUMERIC(19, 6),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE trades (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    qty NUMERIC(19, 6) NOT NULL,
    price NUMERIC(19, 6) NOT NULL,
    fee NUMERIC(19, 6) NOT NULL,
    traded_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_trade_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE positions (
    id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL UNIQUE,
    qty NUMERIC(19, 6) NOT NULL,
    average_price NUMERIC(19, 6) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE market_snapshots (
    id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    price NUMERIC(19, 6) NOT NULL,
    captured_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_orders_symbol ON orders(symbol);
CREATE INDEX idx_orders_external_id ON orders(external_id);
CREATE INDEX idx_trades_order_id ON trades(order_id);
CREATE INDEX idx_positions_symbol ON positions(symbol);
CREATE INDEX idx_market_symbol_time ON market_snapshots(symbol, captured_at DESC);
