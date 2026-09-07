# Paper Money Stock — User Guide

> **Version**: 1.0 · **Mode support**: Simulator (Paper) · Alpaca (Real / Paper API)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Getting Started](#2-getting-started)
3. [Account Snapshot](#3-account-snapshot)
4. [Strategies](#4-strategies)
5. [Simulator Mode](#5-simulator-mode)
6. [Real Live / Alpaca Paper Trading](#6-real-live--alpaca-paper-trading)
7. [Portfolio (Holdings)](#7-portfolio-holdings)
8. [Analytics](#8-analytics)
9. [Market Data](#9-market-data)
10. [Configuration Reference](#10-configuration-reference)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Overview

**Paper Money Stock** is a self-hosted algorithmic trading research platform with two modes:

| Mode | Orders go to | Real money? | Use case |
|---|---|---|---|
| **Simulator** | Local matching engine (in-memory) | Never | Testing strategy logic, backtesting, learning |
| **Alpaca** | Alpaca brokerage API | Paper: No / Live: Yes | Semi-automated or automated real-capital trading |

You can mix brokers **per strategy** — e.g. buy AAPL via Simulator to test, and COST via Alpaca Paper to validate with real market conditions.

---

## 2. Getting Started

### Start the application
```bash
./gradlew bootRun
```
Open: **http://localhost:8080**

### First-time setup
1. The app starts in **Simulator** mode by default (`paperstock.mode: simulator` in `application.yml`)
2. An account with **$100,000 paper cash** is auto-created
3. Add your first strategy via **+ New Strategy**

### Environment variables (optional)
```bash
export ALPACA_API_KEY=your_key
export ALPACA_API_SECRET=your_secret
export FINNHUB_API_KEY=your_key
```

---

## 3. Account Snapshot

Located at the top of the page. Refreshes every 10 seconds.

| Field | Description |
|---|---|
| **Mode** | Current app mode: Simulator or Alpaca |
| **Cash** | Available buying power |
| **Equity** | Total value of all open positions at current market prices |

Simulator: Cash starts at $100,000 and decreases with each buy order.

---

## 4. Strategies

### 4.1 Creating a Strategy

Click **+ New Strategy** (top right of the Strategies panel).

| Field | Description | Example |
|---|---|---|
| **Symbol** | Stock ticker symbol | AAPL, TSLA, COST |
| **Broker** | Which broker executes this strategy | Simulator, Alpaca |
| **Buy Drop %** | How far price must drop below reference to trigger BUY | 5 = buy when price drops 5% |
| **Sell Rise %** | How far price must rise above reference to trigger SELL | 10 = sell when price rises 10% |
| **Buy Cash %** | What % of available cash to use per buy order | 10 = use 10% of cash |
| **Sell Position %** | What % of held position to sell per sell order | 100 = sell entire position |
| **Cooldown (min)** | Minimum minutes between consecutive triggered orders | 30 |
| **Max Orders/Day** | Maximum orders that can execute per calendar day | 3 |

### Reference Price (how it is calculated)
- **No position held**: Uses the **baseline price** (locked at strategy creation from the current live market price)
- **Position held**: Uses the **average buy price** of the current holding

Triggers are stable and do not drift when you inject test prices in Simulator mode.

### 4.2 How Automated Execution Works

The **Strategy Execution Job** runs every `eval-ms` milliseconds (3s for dev testing, 60s for production).

For each active strategy the engine:
1. Checks broker matches app mode (skip if mismatch)
2. Checks cooldown window (skip if within cooldown)
3. Checks daily order limit (skip if reached)
4. Fetches latest market price from DB snapshot (respects Simulator pin)
5. Calculates reference price (baseline or avg buy price)
6. Handles stale quote: refreshes from API unless symbol is Simulator-pinned
7. Skips AI gate entirely in Simulator mode
8. Computes: `buyTrigger = referencePrice * (1 - buyDropPercent/100)`
9. Computes: `sellTrigger = referencePrice * (1 + sellRisePercent/100)`
10. If `currentPrice <= buyTrigger` → fires BUY
11. If `currentPrice >= sellTrigger` → fires SELL
12. Otherwise → records SKIPPED "No threshold crossed"

### 4.3 AI Gate (Adaptive Thresholds)

Active only in **Alpaca mode** when `paperstock.ai.enabled: true`.

The AI layer:
- Analyzes recent sentiment signals
- Computes price volatility from historical snapshots
- Calculates a buy/sell probability using a sigmoid model
- Blocks trades if `probability < threshold` (default 0.55)
- Dynamically widens thresholds in volatile markets

**Simulator mode**: AI gate is always bypassed — you are testing threshold logic directly.

### 4.4 Buy/Sell Report

Located inside the Strategies section (collapsible). Shows aggregated totals of all successful executions grouped by symbol and broker.

| Column | Description |
|---|---|
| Symbol | Stock ticker |
| Broker | Which broker executed the trade |
| Buy Qty | Total shares bought |
| Buy Amount | Total cash spent on buys |
| Buy Trades | Number of successful buy executions |
| Sell Qty | Total shares sold |
| Sell Amount | Total cash received from sells |
| Sell Trades | Number of successful sell executions |

---

## 5. Simulator Mode

### 5.1 What is Simulator Mode?

In Simulator mode:
- All orders go to a **local matching engine** (no real broker)
- A paper account with **$100,000 cash** is auto-created
- Positions and trades are stored in an **in-memory H2 database**
- Data is lost on app restart (switch to file-based H2 for persistence — see Section 10)

### 5.2 Simulator Price Override

This panel appears **only in Simulator mode**, directly below the Strategies section.

Lets you **manually inject a market price** for any symbol. The strategy engine picks up the injected price on its next evaluation cycle (within eval-ms seconds).

#### How to use it

1. Type the stock symbol (e.g. AAPL) in the Symbol field
2. Wait ~350ms — the Trigger Hints panel auto-loads
3. Enter a price in Override Price ($)
4. Click **Set Price**
5. A history entry appears below (symbol, price, timestamp)

#### What happens behind the scenes

```
User sets $290 for AAPL
  → Real market price is read BEFORE overwriting (e.g. $319.97)
  → Baseline price is seeded to $319.97 for all AAPL strategies (if not already set)
  → Market snapshot is overwritten to $290
  → SimulatorPriceRegistry pins AAPL for 5 minutes
  → MarketSnapshotJob skips AAPL while pinned (real API will not overwrite $290)
  → Stale-quote guard in evaluator also respects the pin
  → Strategy evaluator reads $290, sees $290 < $303.97 (buy trigger) → BUY fires
```

#### Price pin (5-minute lock)

After setting a price, the symbol is pinned for 5 minutes. During this time:
- The market data polling job skips fetching real prices for that symbol
- The stale-quote guard skips API refresh for that symbol
- The pinned price persists across eval cycles

After 5 minutes, the pin expires and live market prices resume automatically.

### 5.3 Trigger Hints

When you type a symbol in the Simulator panel, trigger hints appear automatically:

```
Set price <= $303.97  to trigger BUY
Set price >= $351.97  to trigger SELL
Ref price: $319.97                    [Reset Baseline]
```

These values come from:
- **Ref price**: The baseline price locked at strategy creation, or the average buy price if a position is held
- **Buy trigger**: `refPrice * (1 - buyDropPercent/100)`
- **Sell trigger**: `refPrice * (1 + sellRisePercent/100)`

### 5.4 Reset Baseline

The **Reset Baseline** button re-anchors the strategy reference price to the current live market price.

Use this when:
- You want to start a new test cycle from today's real price
- The baseline drifted and you want to reset

When clicked:
1. Fetches the current live market price
2. Saves it as the new baselinePrice for all strategies on that symbol
3. Unpins the symbol (polling resumes with live prices)
4. Trigger hints refresh with the new thresholds

### 5.5 End-to-End Test Walkthrough

```
Step 1: Create a strategy
  Symbol: AAPL, Broker: Simulator
  Buy Drop %: 5, Sell Rise %: 10, Buy Cash %: 10

Step 2: Check the Simulator panel
  Type AAPL — hints show:
    Set price <= $303.97 to trigger BUY
    Set price >= $351.97 to trigger SELL
    Ref price: $319.97

Step 3: Inject a BUY-triggering price
  Override Price: 295 → click Set Price

Step 4: Wait 3 seconds (eval-ms: 3000)
  Strategy engine: $295 < $303.97 → BUY fires
  10% of $100,000 = $10,000 / $295 = 33.9 shares bought

Step 5: Verify in Buy/Sell Report
  AAPL | Simulator | 33.9 shares | $10,000 | 1 trade

Step 6: Test SELL
  After buying, ref price = avg buy price (~$295)
  New sell trigger = $295 * 1.10 = $324.50
  Set price to 330 → wait 3 seconds → SELL fires
```

---

## 6. Real Live / Alpaca Paper Trading

### 6.1 Alpaca Configuration

Alpaca supports two environments:
- **Paper trading**: https://paper-api.alpaca.markets (fake money, real market data)
- **Live trading**: https://api.alpaca.markets (real money)

#### Setup

1. Create an account at alpaca.markets
2. Generate API keys from the Alpaca dashboard
3. Set environment variables:

```bash
export ALPACA_API_KEY=PKXXXXXXXXXXXXXXXX
export ALPACA_API_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export ALPACA_BASE_URL=https://paper-api.alpaca.markets
```

4. Change the app mode in application.yml:

```yaml
paperstock:
  mode: alpaca
```

5. Restart: `./gradlew bootRun`

#### What changes in Alpaca mode

| Feature | Simulator | Alpaca |
|---|---|---|
| Order execution | Local matching engine | Alpaca REST API |
| Cash balance | In-memory ($100K) | Alpaca account balance |
| Positions | In-memory H2 | Alpaca positions |
| AI gate | Bypassed | Active (if enabled) |
| Price override | Available | Not available |

### 6.2 Broker-per-Strategy Assignment

Each strategy has an independent Broker setting:

| Strategy | Symbol | Broker | Purpose |
|---|---|---|---|
| Strategy 1 | AAPL | Simulator | Testing threshold values |
| Strategy 2 | COST | Alpaca | Live paper trading |
| Strategy 3 | TSLA | Alpaca | Real money if live keys set |

The evaluator checks `strategy.broker == app.mode` before evaluating. A Simulator strategy is skipped in Alpaca mode and vice versa.

---

## 7. Portfolio (Holdings)

The Portfolio group shows all manually added or strategy-bought holdings.

### Adding a holding manually
Click **+ Add Holding** inside the Portfolio panel.

### Holdings table

| Column | Description |
|---|---|
| Symbol | Stock ticker |
| Qty | Number of shares held |
| Avg Price | Your average cost basis |
| Live Price | Current market price (auto-refreshes) |
| P&L | Unrealized profit/loss = (livePrice - avgPrice) x qty |
| P&L % | Percentage return on this position |

Click **Add to Strategy** on any holding row to link it to an existing strategy.

---

## 8. Analytics

### Strategy Performance Chart
Visual chart of buy/sell execution prices over time for each strategy.

### Backtest
Test how a strategy would have performed on historical data.

| Field | Description |
|---|---|
| Symbol | Stock to backtest |
| Buy Drop % | Threshold to test |
| Sell Rise % | Threshold to test |
| Start / End Date | Date range for backtest |

Results show: number of BUY/SELL signals, total simulated P&L, comparison to buy-and-hold.

---

## 9. Market Data

### Data sources (in priority order)
1. **Finnhub** (requires API key) — real-time quotes
2. **Yahoo Finance Quote API** — free fallback
3. **Yahoo Chart API** — always works, returns previous close on weekends

### Polling behavior
- Fetches every `poll-ms` milliseconds (3000ms dev, 60000ms production recommended)
- Tracks: symbols with active strategies + portfolio holdings + default symbols list
- Simulator mode: skips pinned symbols for 5 minutes after price injection

### Stale quote handling
- If snapshot older than `max-stale-seconds` (120s) AND not pinned: force-refresh from API
- If symbol IS pinned: skip refresh entirely to protect the injected price

---

## 10. Configuration Reference

### Key settings in application.yml

```yaml
paperstock:
  mode: simulator           # simulator | alpaca
  fee-percentage: 0.001     # 0.1% transaction fee in simulator
  available-liquidity: 100  # Max shares matching engine fills per order

  market-data:
    poll-ms: 60000           # Ms between market data refreshes (3000 for dev)
    max-stale-seconds: 120   # How old a quote can be before force-refresh

  strategy:
    enabled: true
    eval-ms: 60000           # Ms between strategy evaluation cycles (3000 for dev)

  ai:
    enabled: true            # Set false to disable AI gate globally
    probability:
      threshold: 0.55        # Min probability to allow trade (0.0-1.0)
```

### Persistent H2 (survives restarts)

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/paperstock;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE
```

### PostgreSQL

```bash
./gradlew bootRun --args='--spring.profiles.active=postgres'
export POSTGRES_HOST=localhost
export POSTGRES_DB=paperstock
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=secret
```

---

## 11. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| No eval logs | No active strategies | Create a strategy, verify Status = active |
| SKIP broker mismatch | Strategy broker != app mode | Match strategy broker to paperstock.mode |
| SKIP Cooldown active | Within cooldown window | Wait or reduce cooldown-minutes |
| SKIP Daily limit reached | Max orders/day hit | Increase maxOrdersPerDay or wait next day |
| SKIP No current market price | No market snapshot | Wait for first poll cycle |
| Live price overwriting sim price | Pin expired | Re-inject price (resets 5-min pin) |
| Data wiped on restart | In-memory H2 | Switch to file-based H2 (see Section 10) |
| Buy never fires in Simulator | Old issue: AI gate blocking | Confirmed fixed — AI bypassed in Simulator mode |

### DevTools hot-reload wipes data

Spring Boot DevTools reloads the Spring context when source files change. With in-memory H2, this wipes all data. Solutions:
1. Switch to file-based H2 (see Section 10)
2. Stop editing files while testing simulator
3. Use PostgreSQL for persistent storage

---

*For developer and AI agent documentation, see KNOWLEDGE.md*
