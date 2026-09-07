-- Track which side (BUY/SELL) last executed on a strategy.
-- Enables side-aware cooldown: BUY->SELL cross-transitions skip cooldown,
-- same-side repeats (SELL->SELL) are gated to prevent infinite re-sells.
ALTER TABLE strategy_configs ADD COLUMN IF NOT EXISTS last_action_side VARCHAR(10);
