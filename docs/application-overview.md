# PaperStock Application Overview

## What This App Does
PaperStock is a paper trading and strategy automation platform. It lets you:
- manage holdings
- define buy/sell strategies per stock
- monitor live prices and unrealized P&L
- automate strategy execution
- use AI signals for sentiment, dynamic thresholds, and probability gating
- compare AI-enabled and rule-only behavior with backtesting

## Primary User Flows
## 1) Search Stocks and Prepare Strategy
1. Use Stock Search to find symbols.
2. Review live price in search results.
3. Click Fill Strategy to open Strategy Config with selected symbol.

## 2) Configure Strategy Rules
In Strategy Config you set:
- buy drop percent
- sell rise percent
- buy cash percent
- sell position percent
- max orders per day
- cooldown minutes
- mode toggles (simulator/alpaca)

Save Strategy creates or updates the symbol rule set.

## 3) Manage Holdings and P&L
In Holdings and P&L card you can:
- add/update holding manually (symbol, qty, buy price)
- edit line item
- delete line item
- add a holding symbol into Strategy Config quickly
- view current live price and unrealized P&L

## 4) Monitor Strategy Activity
Automation Activity shows each strategy decision/execution with:
- time
- symbol
- side
- status
- message
- order id

## 5) Visualize Price vs Triggers
Price vs Trigger Lines chart displays:
- market price series
- buy trigger line
- sell trigger line
- freshness badge (fresh/stale) based on quote timestamp age

## 6) Review Aggregated Buy/Sell Report
Strategy Buy/Sell Report card summarizes, per symbol:
- total buy quantity and amount
- total sell quantity and amount
- number of buy/sell trades

## 7) Use AI Insights
AI Insights and Backtest card provides:
- current sentiment score per strategy symbol
- live AI decision probabilities and dynamic thresholds
- one-click backtest with side-by-side AI vs rule-only results

## Page Sections and Their Purpose
- Account Snapshot
: top-level status (mode, cash, equity, provider/stale config)
- Stock Search
: discover symbols and prepare strategies
- Strategy Config
: define and control automation logic
- Strategies
: list, edit, pause/resume, delete strategy rules
- Holdings and P&L
: maintain positions and monitor unrealized performance
- Price vs Trigger Lines
: understand trigger behavior over time
- Automation Activity
: audit strategy actions and skip reasons
- Strategy Buy/Sell Report
: aggregated execution outcomes per symbol
- AI Insights and Backtest
: ML-style guidance and offline comparison

## How Automation Works Internally
1. Scheduler wakes up at configured interval.
2. For each active strategy, system validates mode/cooldown/daily limits.
3. System gets latest market quote and checks staleness.
4. AI layer (if enabled) computes sentiment, volatility, probabilities, and dynamic thresholds.
5. If threshold crossed and AI gate allows, app places market order.
6. Execution is recorded in activity and reflected in reports/holdings.

## Refresh Behavior
Top Refresh button updates all cards immediately:
- strategy data
- activity
- reports
- account
- market config
- AI sentiment and decision
- holdings and P&L

Auto-refresh also runs periodically in the dashboard.

## Common Operations
### Add your existing holding
1. In Holdings and P&L, enter symbol, qty, buy price.
2. Click Add/Update Holding.

### Add holding symbol to strategy quickly
1. In holdings table row, click Add to Strategy.
2. Strategy form opens with that symbol.

### Run AI backtest
1. Open AI Insights and Backtest.
2. Select strategy symbol.
3. Set initial cash and max points.
4. Click Run Backtest.

## Interpreting Key Numbers
- Unrealized P&L
: (current live price - buy price) x qty
- Buy/Sell Amount in report
: sum of trade price x trade quantity
- Sentiment Score
: normalized value around -1 to +1 (negative to positive)
- Buy/Sell Probability
: model score used to allow or block signal execution

## Error Scenarios You May See
- Live quotes unavailable
: provider rate-limited or key invalid.
- Backtest insufficient data
: symbol has fewer than 2 snapshots.
- Strategy validation error
: invalid percentages/qty fields.

## Best Practices for Users
- Start in simulator mode.
- Use realistic buy/sell percentages.
- Check stale/fresh indicator before trusting live values.
- Backtest before enabling aggressive automation.
- Review activity messages to understand skipped trades.

## Recommended Next Enhancements
- provider/source tag next to each displayed quote
- per-symbol AI confidence trend chart
- export reports as CSV
- add user auth and role-based access for multi-user environments
