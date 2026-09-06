CREATE TABLE IF NOT EXISTS sentiment_signals (
    id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    source VARCHAR(64) NOT NULL,
    content VARCHAR(2048) NOT NULL,
    score NUMERIC(10, 6) NOT NULL,
    captured_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sentiment_symbol_captured_at
    ON sentiment_signals(symbol, captured_at DESC);