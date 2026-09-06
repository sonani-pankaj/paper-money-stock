# Phase 2 - Config and Infra

## Goal
Configure `WebClient` beans, Caffeine cache, and resilience policies.

## Commands
```bash
docker compose -f infra/docker-compose.yml up -d
./gradlew bootRun -Dspring.profiles.active=postgres
```

## Expected Output
- Postgres container running on 5432
- App starts with postgres profile
- WebClient headers pull Alpaca keys from env vars

## Verification Checklist
- [ ] `alpacaWebClient` and `marketDataWebClient` beans created
- [ ] Caffeine cache named `marketData`
- [ ] Retry/CircuitBreaker instances named `alpacaClient`
