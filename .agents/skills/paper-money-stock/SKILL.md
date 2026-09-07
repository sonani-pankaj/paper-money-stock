---
name: paper-money-stock
description: >
  Knowledge base for the Paper Money Stock application — a Spring Boot algorithmic trading
  simulation platform. Use this skill when making ANY change to this codebase to understand
  the architecture, critical invariants, known bug fixes, and correct data flow patterns.
  Covers: strategy evaluation pipeline, simulator price override, AI gate bypass, market
  data subsystem, REST API surface, database migrations, and frontend architecture.
---

# Paper Money Stock — AI Agent Skill

When working on this project, ALWAYS read the full knowledge base before writing code:

**File**: `docs/KNOWLEDGE.md`
**User Guide**: `docs/userguide.md`

## Critical Rules (memorize these before any change)

1. **capturedAt = LocalDateTime.now()** — Never use Yahoo API timestamps as capturedAt in `refreshAndStore()`. Yahoo returns market-close timestamps (days old on weekends) that break the stale-quote guard.

2. **SimulatorAdapter.placeOrder() must NOT call refreshAndStore** — Would overwrite injected simulator prices before the matching engine runs.

3. **AI gate is bypassed in simulator mode** — `isSimulator` check in `AutomatedStrategyService.evaluateOne()`. Do not remove this bypass.

4. **SimulatorPriceRegistry must be checked before stale-quote refresh** — If symbol is pinned, skip the API refresh in `evaluateOne()`.

5. **MarketSnapshotJob must skip pinned symbols** — Check `simulatorPriceRegistry.isPinned(symbol)` before calling `refreshAndStore` in the poll loop.

6. **baselinePrice is the stable reference** — Never compute buy/sell triggers from the current live price directly. Use `strategy.baselinePrice` or `position.averagePrice`.

7. **Flyway migrations** — Add V{N}__description.sql for any schema changes. Never modify existing migration files.

8. **DevTools + in-memory H2** — Spring Boot DevTools wipes H2 on hot-reload. Advise users to use file-based H2 or PostgreSQL for persistent testing.

## Key Files Quick Reference

| Purpose | File |
|---|---|
| Strategy evaluation | `AutomatedStrategyService.java` |
| Market data fetch + store | `FreeMarketDataService.java` |
| Simulator price injection | `SimulatorController.java` |
| Price pin registry | `SimulatorPriceRegistry.java` |
| Matching engine | `MatchingEngine.java` |
| AI gate | `AiDecisionService.java` |
| Scheduled jobs | `MarketSnapshotJob.java`, `StrategyExecutionJob.java` |
| Config | `application.yml` |
| DB migrations | `src/main/resources/db/migration/V*.sql` |
| Frontend | `src/main/resources/static/index.html`, `app.js`, `style.css` |
