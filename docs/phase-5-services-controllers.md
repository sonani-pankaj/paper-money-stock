# Phase 5 - Services and Controllers

## Goal
Expose trading and market APIs via service delegation.

## Commands
```bash
./gradlew bootRun
curl -X POST http://localhost:8080/api/trade/orders -H "Content-Type: application/json" -d '{"symbol":"AAPL","qty":1,"side":"buy","type":"market"}'
curl http://localhost:8080/api/trade/positions
curl http://localhost:8080/api/account
curl "http://localhost:8080/api/market/latest?symbol=AAPL"
```

## Expected Output
- Order placement returns order DTO with status
- Position list includes AAPL after filled buy order
- Account endpoint returns mode and balances

## Verification Checklist
- [ ] All endpoints return valid JSON
- [ ] Validation blocks invalid request payloads
- [ ] Service performs mode-aware audit behavior
