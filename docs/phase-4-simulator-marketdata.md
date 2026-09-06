# Phase 4 - Simulator and Market Data

## Goal
Implement matching rules, market data refresh, and scheduled snapshot job.

## Commands
```bash
./gradlew test --tests "*MatchingEngineTest"
```

## Expected Output
- Market orders fill at latest snapshot
- Limit orders fill only when price condition matches
- Partial fills respect `paperstock.available-liquidity`

## Verification Checklist
- [ ] Fee uses `paperstock.fee-percentage`
- [ ] Market snapshots are persisted periodically
- [ ] Fallback price logic handles provider errors
