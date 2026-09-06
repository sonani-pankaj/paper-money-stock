# Phase 1 - Domain and Persistence

## Goal
Define entities, DTOs, repositories, and Flyway migration.

## Commands
```bash
./gradlew test --tests "*MatchingEngineTest"
```

## Expected Output
- Flyway migration `V1__init.sql` creates `orders`, `trades`, `positions`, `market_snapshots`
- Repositories load without bean errors

## Verification Checklist
- [ ] JPA entities map to migration tables
- [ ] Enum fields persist as strings
- [ ] Repository methods for top snapshot and lookups work
