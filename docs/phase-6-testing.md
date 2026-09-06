# Phase 6 - Testing and Verification

## Goal
Run unit, adapter, and integration tests for both H2 and Postgres-like flows.

## Commands
```bash
./gradlew test
```

## Additional Commands
```bash
docker compose -f infra/docker-compose.yml up -d
./gradlew bootRun -Dspring.profiles.active=postgres
```

## Expected Output
- All tests pass
- Integration flow creates trade and position for simulator market order

## Verification Checklist
- [ ] `MatchingEngineTest` verifies fee and partial fills
- [ ] `AlpacaAdapterTest` verifies WireMock mapping
- [ ] `SimulatorFlowIntegrationTest` verifies full local flow
- [ ] `SimulatorPostgresIntegrationTest` boots with Testcontainers
