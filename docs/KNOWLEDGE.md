# Paper Money Stock — Developer & AI Agent Knowledge Base

> This document is the authoritative reference for any AI agent or developer making changes to this codebase.
> Read this BEFORE writing any code. It describes architecture, data flow, critical invariants, and known gotchas.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Package Structure](#3-package-structure)
4. [Data Model](#4-data-model)
5. [Core Services](#5-core-services)
6. [Strategy Execution Pipeline](#6-strategy-execution-pipeline)
7. [Simulator Subsystem](#7-simulator-subsystem)
8. [Market Data Subsystem](#8-market-data-subsystem)
9. [AI Gate Subsystem](#9-ai-gate-subsystem)
10. [REST API Surface](#10-rest-api-surface)
11. [Frontend Architecture](#11-frontend-architecture)
12. [Configuration Map](#12-configuration-map)
13. [Database Migrations](#13-database-migrations)
14. [Critical Invariants — Do Not Break](#14-critical-invariants--do-not-break)
15. [Known Bugs and Fixes History](#15-known-bugs-and-fixes-history)
16. [Adding New Features — Checklist](#16-adding-new-features--checklist)

---

## 1. Project Overview

Spring Boot 4 + H2 (in-memory by default) trading simulation platform.

**Two trading modes** (set via `paperstock.mode`):
- `simulator`: all orders routed to local `SimulatorAdapter` + `MatchingEngine`
- `alpaca`: all orders routed to `AlpacaAdapter` (real/paper brokerage REST API)

**Key flows**:
- `MarketSnapshotJob` (scheduled) → fetches live prices → stores to `MarketSnapshotEntity`
- `StrategyExecutionJob` (scheduled) → calls `AutomatedStrategyService.evaluateAllActive()`
- User injects price via `SimulatorController` → `SimulatorPriceRegistry` pins the symbol
- UI polls REST endpoints every 5-10 seconds to refresh data

---

## 2. Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 25, Spring Boot 4.0 |
| Web | Spring WebFlux (reactive, Mono/Flux) |
| Persistence | Spring Data JPA + H2 (default) / PostgreSQL (optional) |
| Migrations | Flyway |
| Scheduling | Spring @Scheduled |
| HTTP Client | Spring WebClient |
| Caching | Caffeine (30s, marketData cache) |
| Build | Gradle |
| Frontend | Vanilla HTML/CSS/JS (single-page, no framework) |
| Dev tools | Spring Boot DevTools (hot-reload; WIPES in-memory H2 on reload) |

---

## 3. Package Structure

```
com.aigrama.papermoney
├── Application.java                  # Entry point
├── adapter/
│   ├── TradingAdapter.java           # Interface: placeOrder, getAccount, getPositions
│   ├── SimulatorAdapter.java         # Implements TradingAdapter for simulator mode
│   └── AlpacaAdapter.java            # Implements TradingAdapter for Alpaca mode
├── config/
│   └── WebClientConfig.java          # WebClient beans
├── controller/
│   ├── AiController.java             # POST /api/ai/sentiment
│   ├── MarketController.java         # GET /api/market/...
│   ├── SimulatorController.java      # POST /api/simulator/price, /reset-baseline
│   ├── StrategyController.java       # CRUD /api/strategies, /report, /chart
│   └── TradingController.java        # GET /api/trading/account, /positions, /orders
├── dto/                              # All request/response DTOs (records)
├── entity/                           # JPA entities
├── job/
│   ├── MarketSnapshotJob.java        # @Scheduled poll-ms — fetches live prices
│   └── StrategyExecutionJob.java     # @Scheduled eval-ms — fires strategy evaluation
├── repository/                       # Spring Data JPA repositories
├── service/
│   ├── AutomatedStrategyService.java # Core strategy evaluation logic
│   ├── AiDecisionService.java        # AI gate: volatility + sentiment → buy/sell probability
│   ├── BacktestService.java          # Historical strategy simulation
│   ├── FreeMarketDataService.java    # Market data fetch + store (implements MarketDataService)
│   ├── MarketDataService.java        # Interface: latestSnapshot, refreshAndStore
│   ├── SentimentService.java         # Reads SentimentSignalEntity for AI scoring
│   ├── StrategyConfigService.java    # CRUD for strategies; sets baselinePrice on create
│   └── TradingService.java           # Routes placeOrder/getAccount to the active TradingAdapter
└── simulator/
    ├── MatchingEngine.java            # Local order matching using latest market snapshot
    └── SimulatorPriceRegistry.java   # In-memory pin registry; pins expire after 5 minutes
```

---

## 4. Data Model

### MarketSnapshotEntity
```
id          UUID (PK)
symbol      VARCHAR — uppercase ticker
price       DECIMAL
capturedAt  TIMESTAMP — ALWAYS set to LocalDateTime.now() when stored (NOT the API timestamp)
```
> CRITICAL: `capturedAt` must be the fetch time, not the market-close time from Yahoo.
> Yahoo often returns timestamps days old on weekends. Using the API timestamp breaks the stale-quote guard.

### StrategyConfigEntity
```
id                UUID (PK)
symbol            VARCHAR
broker            VARCHAR — "simulator" | "alpaca" | "" (case-insensitive match)
active            BOOLEAN
buyDropPercent    DECIMAL
sellRisePercent   DECIMAL
buyCashPercent    DECIMAL
sellPositionPct   DECIMAL
cooldownMinutes   INT
maxOrdersPerDay   INT
baselinePrice     DECIMAL — CRITICAL: locked at strategy creation; null = not yet seeded
lastActionAt      TIMESTAMP — updated after every order to enforce cooldown
```

### PositionEntity
```
id            UUID (PK)
symbol        VARCHAR
qty           DECIMAL
averagePrice  DECIMAL — used as reference price when position is held
```

### AccountStateEntity
```
mode    VARCHAR (PK) — e.g. "DEFAULT"
cash    DECIMAL — starts at 100000; decremented on buy, incremented on sell
```

### StrategyExecutionEntity
```
id              UUID (PK)
strategyId      UUID (FK → StrategyConfigEntity)
symbol          VARCHAR
side            OrderSide (BUY | SELL)
orderId         UUID (nullable — null on SKIPPED/FAILED)
executedPrice   DECIMAL
referencePrice  DECIMAL
status          StrategyExecutionStatus (SUCCESS | SKIPPED | FAILED)
reason          VARCHAR
executedAt      TIMESTAMP
```

### SentimentSignalEntity
```
id          UUID (PK)
symbol      VARCHAR
score       DECIMAL (-1.0 to 1.0)
source      VARCHAR
recordedAt  TIMESTAMP
```

---

## 5. Core Services

### TradingService
Routes `placeOrder` and `getAccount` to the active `TradingAdapter` implementation.
Which adapter is active depends on `paperstock.mode`:
- `simulator` → `SimulatorAdapter`
- `alpaca` → `AlpacaAdapter`

Selection is done via `@ConditionalOnProperty` or a qualifier — check `TradingService` for current mechanism.

### FreeMarketDataService (implements MarketDataService)

Two key methods:

**`refreshAndStore(symbol)`**
- Calls `fetchQuote(symbol)` which tries: Finnhub → Yahoo Quote → Yahoo Chart
- Stores result with `capturedAt = LocalDateTime.now()` (NOT the API timestamp)
- Returns `Mono<MarketSnapshotDto>`

**`latestSnapshot(symbol)`**
- Reads `MarketSnapshotRepository.findTopBySymbolOrderByCapturedAtDesc(symbol)`
- Returns `Mono<MarketSnapshotDto>` (empty if no snapshot exists)

### StrategyConfigService

**`create(request)`**
- Calls `latestSnapshot(symbol)` to get real price
- Sets `baselinePrice = currentPrice` on the new entity
- If no snapshot exists yet, baselinePrice remains null (seeded later by SimulatorController or evaluator)

---

## 6. Strategy Execution Pipeline

### Entry point
`StrategyExecutionJob.evaluate()` → `AutomatedStrategyService.evaluateAllActive()`

### evaluateAllActive()
```java
List<StrategyConfigEntity> strategies = strategyConfigRepository.findAllByActiveTrue();
for (StrategyConfigEntity strategy : strategies) {
    try { evaluateOne(strategy); }
    catch (Exception ex) { record(FAILED, ex.getMessage()); }
}
```

### evaluateOne(strategy) — ANNOTATED FLOW
```
1. isModeAllowed(strategy)
   → strategy.broker.equalsIgnoreCase(mode) — case-insensitive
   → If broker is blank/null, ALWAYS allows (broker-agnostic strategy)
   → SKIP if mismatch

2. isCooldownActive(strategy)
   → strategy.lastActionAt + cooldownMinutes > now
   → SKIP if within cooldown

3. isDailyLimitReached(strategy)
   → Count of SUCCESS executions today >= maxOrdersPerDay
   → SKIP if reached

4. Fetch price
   → marketDataService.latestSnapshot(symbol)
   → If empty: switchIfEmpty(refreshAndStore) — only refreshes if NO snapshot exists
   → currentPrice = snapshot.price()
   → SKIP if null or <= 0

5. referencePrice selection (priority order)
   → position.averagePrice (if position exists and avgPrice > 0)
   → strategy.baselinePrice (if set and > 0)
   → currentPrice (last resort, moving reference — not ideal)

6. Stale quote check
   → ageSeconds = now - snapshot.capturedAt
   → If ageSeconds > maxStaleSeconds:
       → If simulatorPriceRegistry.isPinned(symbol): SKIP refresh (protect injected price)
       → Else: call refreshAndStore to get fresh price
   → SKIP if refresh fails

7. AI gate (ONLY if !isSimulator)
   → isSimulator = "simulator".equalsIgnoreCase(strategy.broker) || "simulator".equalsIgnoreCase(mode)
   → If isSimulator: bypass AI gate entirely
   → Else: AiDecisionService.evaluate() → allowBuy, allowSell, dynamicDropPct, dynamicRisePct

8. Compute triggers
   → buyTrigger = referencePrice * (1 - buyDropPercent/100)
   → sellTrigger = referencePrice * (1 + sellRisePercent/100)

9. AI block check (only if not simulator)
   → If !allowBuy && currentPrice <= buyTrigger: SKIP "AI gate blocked BUY"
   → If !allowSell && currentPrice >= sellTrigger: SKIP "AI gate blocked SELL"

10. Execute
    → currentPrice <= buyTrigger → executeBuy()
    → currentPrice >= sellTrigger → executeSell()
    → Otherwise → record SKIPPED "No threshold crossed"
```

### executeBuy(strategy, currentPrice, referencePrice)
```
1. tradingService.getAccount().block() — check cash > 0
2. cashToUse = account.cash * (buyCashPercent/100)
3. qty = cashToUse / currentPrice
4. qty <= 0 → SKIP "Computed buy qty is zero"
5. tradingService.placeOrder(PlaceOrderRequestDto).block(8s)
6. touchStrategyAction(strategy) — updates lastActionAt
7. record(SUCCESS, "BUY executed")
```

---

## 7. Simulator Subsystem

### SimulatorPriceRegistry
```java
// In-memory map: symbol → pinExpiry (LocalDateTime)
// Pin duration: 5 minutes
// Methods:
void pin(String symbol)        // Sets expiry = now + 5 min
void unpin(String symbol)      // Removes from map
boolean isPinned(String symbol) // True if expiry is in the future
```

Location: `com.aigrama.papermoney.simulator.SimulatorPriceRegistry`

### SimulatorController — POST /api/simulator/price
```
Request: { symbol, price }
1. Reads current live price (latestSnapshot or refreshAndStore)
2. Saves this as realMarketPrice
3. Backfills baselinePrice for all strategies on symbol if baselinePrice == null
4. Overwrites market snapshot with injected price (capturedAt = now)
5. Calls registry.pin(symbol)
6. Returns: { symbol, injectedPrice, realMarketPrice, pinnedUntil }
```

### SimulatorController — POST /api/simulator/reset-baseline
```
Request: { symbol }
1. Calls refreshAndStore(symbol) to get current live price
2. Updates baselinePrice = livePrice on all strategies for symbol
3. Calls registry.unpin(symbol)
4. Returns: { symbol, newBaselinePrice }
```

### SimulatorAdapter.placeOrder()
```java
// DO NOT call refreshAndStore here — would overwrite injected simulator price
return Mono.fromCallable(() -> placeOrderTx(req))
           .subscribeOn(Schedulers.boundedElastic());
```

### MatchingEngine.match(order)
- Reads `findTopBySymbolOrderByCapturedAtDesc(symbol)` — gets latest snapshot price
- For MARKET orders: always fillable
- `fillQty = order.qty.min(availableLiquidity)` — capped by liquidity setting
- Returns `MatchResult(filledQty, fillPrice, fee, trades)`

### MarketSnapshotJob — respects pin
```java
// In refresh loop:
if (simulatorPriceRegistry.isPinned(symbol)) {
    log.debug("Skipping pinned symbol: {}", symbol);
    continue; // Do NOT call refreshAndStore for pinned symbols
}
```

---

## 8. Market Data Subsystem

### Fetch chain (FreeMarketDataService.fetchQuote)
```
1. Finnhub API (requires FINNHUB_API_KEY)
   → GET /api/v1/quote?symbol={symbol}&token={key}
   → Returns: { c: currentPrice, t: timestamp }

2. Yahoo Finance Quote API (no key needed)
   → GET /v7/finance/quote?symbols={symbol}
   → Sometimes returns 401 or rate-limited

3. Yahoo Chart API (no key needed — most reliable)
   → GET /v8/finance/chart/{symbol}?interval=1d&range=1d
   → ALWAYS returns data (previous close on weekends/holidays)
   → timestamp in response may be days old — DO NOT use as capturedAt
```

### capturedAt rule
**ALWAYS** set `capturedAt = LocalDateTime.now()` when storing via `refreshAndStore`.
Never use `quote.timestamp()` from the API response — Yahoo returns market-close timestamps
that can be days old on weekends, which breaks the 120-second stale-quote guard.

### MarketSnapshotJob scheduling
```java
@Scheduled(fixedDelayString = "${paperstock.market-data.poll-ms:30000}")
// fixedDelay = N ms AFTER last execution completes (not a fixed rate)
// Default: 30000ms. Configured to 3000ms for dev testing.
```

---

## 9. AI Gate Subsystem

### AiDecisionService.evaluate(symbol, baseBuyDrop, baseSellRise, currentPrice)

**Volatility estimate** (estimateVolatilityPct):
- Reads last 30 snapshots from DB
- Computes mean absolute return = avg(|price_i - price_{i-1}| / price_{i-1})
- Returns 0 if fewer than 2 snapshots

**Momentum estimate** (estimateMomentumPct):
- Reads last 8 snapshots
- Returns `(currentPrice - oldestPrice) / oldestPrice`
- Negative momentum = price has been falling

**Buy probability** (sigmoid model):
```
input = -0.15 + sentiment*0.9 + momentum*1.3 - volatility*0.8
buyProbability = sigmoid(input) = 1 / (1 + e^(-input))
```

**Decision**:
```
allowBuy = buyProbability >= probabilityThreshold (default 0.55)
```

### When AI gate is bypassed
```java
boolean isSimulator = "simulator".equalsIgnoreCase(strategy.getBroker())
        || "simulator".equalsIgnoreCase(mode);
if (aiEnabled && !isSimulator) {
    // call AiDecisionService
}
```

The bypass is intentional: injecting a lower price in the simulator creates artificial negative momentum
which would set buyProbability < 0.55 and block the buy — defeating the purpose of simulator testing.

---

## 10. REST API Surface

### Market
```
GET  /api/market/symbols          — list of all tracked symbols
GET  /api/market/quote/{symbol}   — latest price snapshot
GET  /api/market/search?q=...     — symbol search
```

### Strategies
```
GET    /api/strategies              — all strategies
POST   /api/strategies              — create strategy (body: StrategyConfigRequestDto)
PUT    /api/strategies/{id}         — update strategy
DELETE /api/strategies/{id}         — delete strategy
POST   /api/strategies/{id}/pause   — set active=false
POST   /api/strategies/{id}/resume  — set active=true
GET    /api/strategies/report       — buy/sell report (StrategyTradeReportDto list)
GET    /api/strategies/chart/{id}   — execution history for chart
GET    /api/strategies/triggers/{symbol} — trigger hints (buyTrigger, sellTrigger, refPrice)
```

### Simulator (only meaningful in simulator mode)
```
POST /api/simulator/price           — { symbol, price } → inject price + pin
POST /api/simulator/reset-baseline  — { symbol } → reset baselinePrice to live price
```

### Trading
```
GET  /api/trading/account           — AccountDto (cash, equity, mode)
GET  /api/trading/positions         — list of PositionDto
GET  /api/trading/orders            — list of OrderDto
POST /api/trading/orders            — PlaceOrderRequestDto → OrderDto
POST /api/trading/account           — UpdateAccountRequestDto (set cash manually)
POST /api/trading/holdings          — ManualHoldingRequestDto (add manual holding)
```

### AI
```
POST /api/ai/sentiment              — SentimentSignalRequestDto { symbol, score, source }
GET  /api/ai/decision/{symbol}      — AiDecisionDto (current AI assessment)
```

---

## 11. Frontend Architecture

Single-page app: `src/main/resources/static/index.html` + `app.js` + `style.css`

### Key UI sections (in DOM order)
1. **Account Snapshot** — polls `/api/trading/account` every 10s
2. **Strategies** — polls `/api/strategies` every 5s; contains Buy/Sell Report (collapsible)
3. **Simulator Price Override** — shown only when mode=simulator; polls `/api/simulator/price` status
4. **Portfolio** — polls `/api/trading/positions` every 10s
5. **Analytics** — backtesting UI (manual trigger)

### Simulator trigger hints flow (app.js)
```
User types symbol → debounce 350ms → GET /api/strategies/triggers/{symbol}
→ Response: { buyTriggerPrice, sellTriggerPrice, referencePrice }
→ Renders hint rows + Reset Baseline button
```

### Price injection flow (app.js)
```
User clicks Set Price → POST /api/simulator/price { symbol, price }
→ Response: { symbol, injectedPrice, realMarketPrice, pinnedUntil }
→ Appends history entry below form
→ Updates trigger hints
```

---

## 12. Configuration Map

| YML key | Java binding | Default | Description |
|---|---|---|---|
| `paperstock.mode` | `AutomatedStrategyService.mode` | `simulator` | Trading mode |
| `paperstock.fee-percentage` | `MatchingEngine.feePercentage` | `0.001` | Simulator fee |
| `paperstock.available-liquidity` | `MatchingEngine.availableLiquidity` | `100` | Max fill qty |
| `paperstock.market-data.poll-ms` | `MarketSnapshotJob` `@Scheduled` | `30000` | Poll interval |
| `paperstock.market-data.max-stale-seconds` | `AutomatedStrategyService.maxStaleSeconds` | `120` | Stale threshold |
| `paperstock.strategy.eval-ms` | `StrategyExecutionJob` `@Scheduled` | `30000` | Eval interval |
| `paperstock.ai.enabled` | `AutomatedStrategyService.aiEnabled` | `true` | AI gate toggle |
| `paperstock.ai.probability.threshold` | `AiDecisionService.probabilityThreshold` | `0.55` | Min trade probability |
| `paperstock.symbols` | `MarketSnapshotJob` | `[AAPL, TSLA]` | Default tracked symbols |

---

## 13. Database Migrations

Flyway migrations in `src/main/resources/db/migration/`:

| File | Description |
|---|---|
| V1__init.sql | Core tables: market_snapshot, account_state, position, order, trade |
| V2__strategy_tables.sql | strategy_config, strategy_execution tables |
| V3__ai_sentiment_signals.sql | sentiment_signal table for AI inputs |
| V4__strategy_broker.sql | Added broker column to strategy_config |
| V5__strategy_baseline_price.sql | Added baseline_price column to strategy_config |

**To add a new column**: Create `V6__description.sql` with `ALTER TABLE` statements.
Never modify existing migration files — Flyway checksums will fail.

---

## 14. Critical Invariants — Do Not Break

### INV-1: capturedAt must be LocalDateTime.now()
In `FreeMarketDataService.refreshAndStore()`, the entity's `capturedAt` MUST be set to
`LocalDateTime.now()` at fetch time. Do NOT use `quote.timestamp()` from the API response.
Yahoo Chart returns market-close timestamps (can be 3+ days old on weekends), which makes
the 120-second stale-quote guard reject all evaluation for the entire weekend.

### INV-2: SimulatorAdapter.placeOrder must NOT call refreshAndStore
If `SimulatorAdapter.placeOrder` calls `refreshAndStore` before matching, it overwrites the
manually injected simulator price with the real market price, making the price injection useless.
The matching engine uses whatever is in the snapshot DB — leave it as-is.

### INV-3: AI gate must be bypassed in simulator mode
The AI uses momentum (price direction) to compute buy probability. Injecting a lower price
in the simulator creates artificial negative momentum → buyProbability drops below 0.55 →
AI blocks the buy. This defeats simulator testing. Always check `isSimulator` flag before
calling `AiDecisionService.evaluate()`.

### INV-4: SimulatorPriceRegistry must be respected in stale-quote check
When a symbol is pinned (user injected a price), the stale-quote refresh inside `evaluateOne()`
must NOT call `refreshAndStore` for that symbol. Doing so overwrites the injected price.
Always check `simulatorPriceRegistry.isPinned(symbol)` before refreshing.

### INV-5: MarketSnapshotJob must skip pinned symbols
The polling job runs every poll-ms and would normally overwrite injected prices.
It must check `simulatorPriceRegistry.isPinned(symbol)` and skip those symbols.

### INV-6: baselinePrice is the stable reference (not the current market price)
The evaluator uses `strategy.baselinePrice` (or position avg price) as the reference,
NOT the current live price. This prevents the buy/sell thresholds from "moving" when
prices fluctuate or when a different test price is injected.

### INV-7: Devtools hot-reload wipes in-memory H2
Spring Boot DevTools triggers a Spring context restart when any file in the classpath changes.
This wipes all in-memory H2 data (strategies, positions, orders). During simulator testing,
advise users to not edit files. For persistent testing, use file-based H2 or PostgreSQL.

---

## 15. Known Bugs and Fixes History

### [FIXED] Stale quote blocking all evaluations on weekends
- **Root cause**: `capturedAt` was set from Yahoo's API timestamp (market close, days old)
- **Fix**: `refreshAndStore` now uses `LocalDateTime.now()` instead of `quote.timestamp()`
- **File**: `FreeMarketDataService.java` line ~89

### [FIXED] AI gate blocking valid Simulator buys
- **Root cause**: Injecting lower price → negative momentum → buyProbability < 0.55 → blocked
- **Fix**: `isSimulator` check bypasses entire AI gate for simulator-mode strategies
- **File**: `AutomatedStrategyService.java` evaluateOne() method

### [FIXED] refreshAndStore in placeOrder overwriting injected price
- **Root cause**: `SimulatorAdapter.placeOrder()` called `refreshAndStore` before matching
- **Fix**: Removed `refreshAndStore` call; matching engine uses existing snapshot
- **File**: `SimulatorAdapter.java` placeOrder() method

### [FIXED] Stale-quote refresh overwriting pinned simulator price after 120s
- **Root cause**: Even with pin, the stale guard called `refreshAndStore` after 120s
- **Fix**: Check `simulatorPriceRegistry.isPinned(symbol)` before doing stale refresh
- **File**: `AutomatedStrategyService.java` evaluateOne() stale-quote section

### [FIXED] Baseline price drifting with injected prices
- **Root cause**: Reference price was computed from current live price (moved on injection)
- **Fix**: Added `baselinePrice` column to strategy; seeded at creation time; pin injection
  also seeds baseline from real price before overwriting snapshot
- **File**: `StrategyConfigEntity.java`, `SimulatorController.java`, `StrategyConfigService.java`

---

## 16. Adding New Features — Checklist

### Adding a new strategy parameter
1. Add column to `StrategyConfigEntity.java`
2. Add `V6__*.sql` migration with `ALTER TABLE strategy_config ADD COLUMN ...`
3. Add field to `StrategyConfigRequestDto.java` and `StrategyConfigDto.java`
4. Update `StrategyConfigService.create()` and `update()` to map the field
5. Add the field to the modal form in `index.html`
6. Wire the field in the `app.js` strategy creation/update handler
7. Use the field in `AutomatedStrategyService.evaluateOne()` if it affects evaluation

### Adding a new broker adapter
1. Create `NewBrokerAdapter.java` implementing `TradingAdapter`
2. Add `@ConditionalOnProperty(name="paperstock.mode", havingValue="newbroker")` 
3. Add `newbroker` to the valid values in documentation
4. Add the broker name to the strategy modal dropdown in `index.html`
5. Handle the mode in `AutomatedStrategyService.isModeAllowed()`

### Adding a new REST endpoint
1. Add method to the appropriate controller
2. Add corresponding `@GetMapping` / `@PostMapping` with full path
3. Add the fetch call in `app.js`
4. Update this KNOWLEDGE.md REST API surface section

### Adding a new scheduled job
1. Annotate with `@Scheduled(fixedDelayString = "${paperstock.your-job.interval-ms:30000}")`
2. Add the config key to `application.yml`
3. Add to the Configuration Map table above (Section 12)
4. Ensure the job does NOT conflict with `SimulatorPriceRegistry` pins

### Changing the H2 schema
- ALWAYS add a new Flyway migration file (`V{N}__description.sql`)
- NEVER modify existing migration files (Flyway checksums will fail and app won't start)
- Test migration applies cleanly to a fresh in-memory DB

---

*Last updated: 2026-09-07*
*User guide: userguide.md*
