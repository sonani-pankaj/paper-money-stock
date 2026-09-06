# Phase 3 - Adapters

## Goal
Implement `TradingAdapter` contract with Alpaca and Simulator implementations.

## Commands
```bash
./gradlew test --tests "*AlpacaAdapterTest"
```

## Expected Output
- WireMock test validates Alpaca JSON mapping to DTOs
- Simulator adapter persists order/trade/position updates transactionally

## Verification Checklist
- [ ] `paperstock.mode=alpaca` activates `AlpacaAdapter`
- [ ] `paperstock.mode=simulator` activates `SimulatorAdapter`
- [ ] Alpaca calls use retry, circuit breaker, and timeout
