const $ = (id) => document.getElementById(id);

const state = {
    strategies: [],
    chart: null,
    activityRows: [],
    holdingsRows: [],
    stockSearchRows: [],
    strategyReportRows: [],
    aiSentimentRows: []
};

async function api(url, options) {
    const response = await fetch(url, {
        headers: { "Content-Type": "application/json" },
        ...options
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }

    if (response.status === 204) {
        return null;
    }
    return response.json();
}

function showError(error) {
    console.error(error);
    showToast(`Request failed: ${error.message}`, "error");
}

function showToast(message, variant = "success") {
    const host = $("toastHost");
    if (!host) {
        return;
    }

    const toast = document.createElement("div");
    toast.className = `toast ${variant}`;
    toast.textContent = message;
    host.appendChild(toast);

    window.setTimeout(() => {
        toast.style.opacity = "0";
        toast.style.transform = "translateY(6px)";
    }, 2600);

    window.setTimeout(() => {
        toast.remove();
    }, 2900);
}

function includesFilter(text, filter) {
    if (!filter) {
        return true;
    }
    return String(text || "").toLowerCase().includes(filter.toLowerCase());
}

function toMoney(value) {
    const num = Number(value || 0);
    return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(num);
}

function toNum(value) {
    return Number(value || 0).toFixed(4);
}

function toPct(value) {
    if (value == null) {
        return "-";
    }
    return `${(Number(value) * 100).toFixed(2)}%`;
}

function escapeHtml(text) {
    return String(text || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function fillForm(strategy) {
    $("strategyId").value = strategy.id;
    $("symbol").value = strategy.symbol;
    $("buyDropPercent").value = strategy.buyDropPercent;
    $("sellRisePercent").value = strategy.sellRisePercent;
    $("buyCashPercent").value = strategy.buyCashPercent;
    $("sellPositionPercent").value = strategy.sellPositionPercent;
    $("maxOrdersPerDay").value = strategy.maxOrdersPerDay;
    $("cooldownMinutes").value = strategy.cooldownMinutes;
    $("active").checked = strategy.active;
    $("simulatorEnabled").checked = strategy.simulatorEnabled;
    $("alpacaEnabled").checked = strategy.alpacaEnabled;
}

async function onStrategyTableClick(event) {
    const btn = event.target.closest("button[data-action]");
    if (!btn) {
        return;
    }

    const strategy = state.strategies.find((s) => s.id === btn.dataset.id);
    if (!strategy) {
        showToast("Strategy not found in current view.", "error");
        return;
    }

    const action = btn.dataset.action;
    if (action === "edit") {
        fillForm(strategy);
        $("buyDropPercent").focus();
        showToast(`Editing strategy for ${strategy.symbol}`);
        return;
    }

    if (action === "toggle") {
        try {
            const path = strategy.active ? "pause" : "resume";
            await api(`/api/strategies/${strategy.id}/${path}`, { method: "POST" });
            await refreshAll();
        } catch (error) {
            showError(error);
        }
        return;
    }

    if (action === "delete") {
        try {
            if (!confirm(`Delete strategy for ${strategy.symbol}?`)) {
                return;
            }
            await api(`/api/strategies/${strategy.id}`, { method: "DELETE" });
            clearForm();
            await refreshAll();
        } catch (error) {
            showError(error);
        }
    }
}

function clearForm() {
    $("strategyId").value = "";
    $("strategyForm").reset();
    $("active").checked = true;
    $("simulatorEnabled").checked = true;
    $("alpacaEnabled").checked = true;
    $("buyDropPercent").value = "5";
    $("sellRisePercent").value = "10";
    $("buyCashPercent").value = "10";
    $("sellPositionPercent").value = "25";
    $("maxOrdersPerDay").value = "2";
    $("cooldownMinutes").value = "30";
}

function startNewStrategyForSymbol(symbol) {
    clearForm();
    const normalized = String(symbol || "").toUpperCase();
    $("symbol").value = normalized;
    $("symbol").focus();
    showToast(`Strategy form ready for ${normalized}`);
}

function openStrategyFromHolding(symbol) {
    const normalized = String(symbol || "").toUpperCase();
    const existing = state.strategies.find((s) => s.symbol === normalized);
    if (existing) {
        fillForm(existing);
        $("buyDropPercent").focus();
        showToast(`Loaded existing strategy for ${normalized}.`);
    } else {
        startNewStrategyForSymbol(normalized);
    }
    $("strategyForm").scrollIntoView({ behavior: "smooth", block: "start" });
}

async function onHoldingsTableClick(event) {
    const btn = event.target.closest("button[data-holding-action]");
    if (!btn) {
        return;
    }

    const symbol = String(btn.dataset.symbol || "").toUpperCase();
    const row = state.holdingsRows.find((h) => h.symbol === symbol);
    if (!row) {
        showToast("Holding row not found.", "error");
        return;
    }

    const action = btn.dataset.holdingAction;
    if (action === "edit") {
        $("holdingSymbol").value = row.symbol;
        $("holdingQty").value = Number(row.qty || 0);
        $("holdingBuyPrice").value = Number(row.avg || 0);
        $("holdingQty").focus();
        showToast(`Editing holding for ${row.symbol}.`);
        return;
    }

    if (action === "strategy") {
        openStrategyFromHolding(row.symbol);
        return;
    }

    if (action === "delete") {
        if (!confirm(`Delete holding for ${row.symbol}?`)) {
            return;
        }
        await api(`/api/trade/positions/${encodeURIComponent(row.symbol)}`, { method: "DELETE" });
        showToast(`Deleted holding for ${row.symbol}.`);
        await loadHoldings();
    }
}

function strategyPayloadFromForm() {
    return {
        symbol: $("symbol").value.trim().toUpperCase(),
        buyDropPercent: Number($("buyDropPercent").value),
        sellRisePercent: Number($("sellRisePercent").value),
        buyCashPercent: Number($("buyCashPercent").value),
        sellPositionPercent: Number($("sellPositionPercent").value),
        maxOrdersPerDay: Number($("maxOrdersPerDay").value),
        cooldownMinutes: Number($("cooldownMinutes").value),
        active: $("active").checked,
        simulatorEnabled: $("simulatorEnabled").checked,
        alpacaEnabled: $("alpacaEnabled").checked
    };
}

async function saveStrategy(event) {
    event.preventDefault();
    let id = $("strategyId").value;
    const payload = strategyPayloadFromForm();

    try {
        if (!id) {
            const existing = state.strategies.find((s) => s.symbol === payload.symbol);
            if (existing) {
                id = existing.id;
                showToast(`Strategy for ${payload.symbol} exists, updating it.`);
            }
        }

        if (id) {
            await api(`/api/strategies/${id}`, { method: "PUT", body: JSON.stringify(payload) });
        } else {
            await api("/api/strategies", { method: "POST", body: JSON.stringify(payload) });
        }
        clearForm();
        await refreshAll();
    } catch (error) {
        showError(error);
    }
}

function renderStrategies() {
    const body = $("strategyTableBody");
    const select = $("chartStrategySelect");
    body.innerHTML = "";
    select.innerHTML = "";

    state.strategies.forEach((s) => {
        const tr = document.createElement("tr");
        const statusClass = s.active ? "good" : "warn";
        tr.innerHTML = `
            <td class="mono">${s.symbol}</td>
            <td>${toNum(s.buyDropPercent)}%</td>
            <td>${toNum(s.sellRisePercent)}%</td>
            <td><span class="tag ${statusClass}">${s.active ? "active" : "paused"}</span></td>
            <td>${s.simulatorEnabled ? "sim" : ""}${s.alpacaEnabled ? " alpaca" : ""}</td>
            <td>
                <button type="button" data-id="${s.id}" data-action="edit" class="btn">Edit</button>
                <button type="button" data-id="${s.id}" data-action="toggle" class="btn">${s.active ? "Pause" : "Resume"}</button>
                <button type="button" data-id="${s.id}" data-action="delete" class="btn">Delete</button>
            </td>
        `;
        body.appendChild(tr);

        const option = document.createElement("option");
        option.value = s.id;
        option.textContent = `${s.symbol} (${s.id.slice(0, 8)})`;
        select.appendChild(option);
    });

    if (state.strategies.length > 0) {
        if (!select.value) {
            select.value = state.strategies[0].id;
        }
        loadChart(select.value).catch(showError);
    }
}

function renderStockSearchResults() {
    const body = $("stockSearchBody");
    body.innerHTML = "";

    state.stockSearchRows.forEach((row) => {
        const priceText = row.currentPrice == null ? "-" : toMoney(row.currentPrice);
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td class="mono">${escapeHtml(row.symbol)}</td>
            <td>${escapeHtml(row.name || "-")}</td>
            <td>${escapeHtml(row.exchange || "-")}</td>
            <td class="mono">${priceText}</td>
            <td>
                <button class="btn" data-action="use" data-symbol="${escapeHtml(row.symbol)}">Fill Strategy</button>
            </td>
        `;
        body.appendChild(tr);
    });

    body.querySelectorAll("button[data-action='use']").forEach((btn) => {
        btn.addEventListener("click", () => {
            startNewStrategyForSymbol(btn.dataset.symbol);
        });
    });
}

async function searchStocks() {
    const query = $("stockSearchQuery").value.trim();
    if (!query) {
        state.stockSearchRows = [];
        renderStockSearchResults();
        return;
    }

    const results = await api(`/api/market/search?q=${encodeURIComponent(query)}&limit=12`);
    state.stockSearchRows = await Promise.all(results.map(async (row) => {
        try {
            const quote = await api(`/api/market/latest?symbol=${encodeURIComponent(row.symbol)}&refresh=true`);
            return { ...row, currentPrice: Number(quote.price) };
        } catch (error) {
            return { ...row, currentPrice: null };
        }
    }));

    if (state.stockSearchRows.length > 0 && state.stockSearchRows.every((row) => row.currentPrice == null)) {
        showToast("Live quotes unavailable right now. Configure TWELVEDATA_API_KEY or retry later.", "error");
    }

    renderStockSearchResults();
}

async function loadStrategies() {
    state.strategies = await api("/api/strategies");
    renderStrategies();
    renderAiSymbolSelect();
}

function renderAiSymbolSelect() {
    const select = $("aiSymbolSelect");
    if (!select) {
        return;
    }
    const prev = select.value;
    select.innerHTML = "";
    state.strategies.forEach((s) => {
        const option = document.createElement("option");
        option.value = s.symbol;
        option.textContent = s.symbol;
        select.appendChild(option);
    });
    if (prev && state.strategies.some((s) => s.symbol === prev)) {
        select.value = prev;
    }
}

async function loadAiSentimentByStrategy() {
    const rows = [];
    for (const strategy of state.strategies) {
        try {
            const score = await api(`/api/ai/sentiment/${encodeURIComponent(strategy.symbol)}`);
            rows.push(score);
        } catch (error) {
            rows.push({ symbol: strategy.symbol, score: null, sampleSize: 0 });
        }
    }
    state.aiSentimentRows = rows;
    renderAiSentiment();
}

function renderAiSentiment() {
    const body = $("aiSentimentBody");
    body.innerHTML = "";
    for (const row of state.aiSentimentRows) {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td class="mono">${escapeHtml(row.symbol)}</td>
            <td>${row.score == null ? "-" : Number(row.score).toFixed(4)}</td>
            <td>${Number(row.sampleSize || 0)}</td>
        `;
        body.appendChild(tr);
    }
}

async function refreshAiDecision() {
    const symbol = $("aiSymbolSelect").value;
    if (!symbol) {
        $("aiCurrentPrice").textContent = "-";
        $("aiBuyProb").textContent = "-";
        $("aiSellProb").textContent = "-";
        $("aiDynamicBuy").textContent = "-";
        $("aiDynamicSell").textContent = "-";
        $("aiGate").textContent = "-";
        return;
    }

    const strategy = state.strategies.find((s) => s.symbol === symbol);
    if (!strategy) {
        return;
    }

    const market = await api(`/api/market/latest?symbol=${encodeURIComponent(symbol)}&refresh=true`);
    const currentPrice = Number(market.price);
    const decision = await api(
        `/api/ai/decision?symbol=${encodeURIComponent(symbol)}&buyDropPercent=${strategy.buyDropPercent}&sellRisePercent=${strategy.sellRisePercent}&currentPrice=${currentPrice}`
    );

    $("aiCurrentPrice").textContent = toMoney(currentPrice);
    $("aiBuyProb").textContent = toPct(decision.buyProbability);
    $("aiSellProb").textContent = toPct(decision.sellProbability);
    $("aiDynamicBuy").textContent = `${Number(decision.dynamicBuyDropPercent).toFixed(2)}%`;
    $("aiDynamicSell").textContent = `${Number(decision.dynamicSellRisePercent).toFixed(2)}%`;
    $("aiGate").textContent = `${decision.allowBuy ? "BUY yes" : "BUY no"} / ${decision.allowSell ? "SELL yes" : "SELL no"}`;
}

async function runBacktest(event) {
    event.preventDefault();
    const symbol = $("aiSymbolSelect").value;
    const strategy = state.strategies.find((s) => s.symbol === symbol);
    if (!strategy) {
        showToast("Select a strategy symbol first.", "error");
        return;
    }

    const payload = {
        symbol,
        initialCash: Number($("backtestInitialCash").value),
        buyDropPercent: Number(strategy.buyDropPercent),
        sellRisePercent: Number(strategy.sellRisePercent),
        buyCashPercent: Number(strategy.buyCashPercent),
        sellPositionPercent: Number(strategy.sellPositionPercent),
        maxPoints: Number($("backtestMaxPoints").value)
    };

    const result = await api("/api/ai/backtest", {
        method: "POST",
        body: JSON.stringify(payload)
    });
    renderBacktest(result);
    showToast(`Backtest completed for ${symbol}.`);
}

function renderBacktest(data) {
    const body = $("backtestBody");
    body.innerHTML = "";
    if (!data || !data.aiEnabled || !data.ruleOnly) {
        return;
    }

    const rows = [
        ["Buy Trades", data.aiEnabled.buyTrades, data.ruleOnly.buyTrades],
        ["Sell Trades", data.aiEnabled.sellTrades, data.ruleOnly.sellTrades],
        ["Ending Cash", toMoney(data.aiEnabled.endingCash), toMoney(data.ruleOnly.endingCash)],
        ["Ending Qty", toNum(data.aiEnabled.endingQty), toNum(data.ruleOnly.endingQty)],
        ["Ending Value", toMoney(data.aiEnabled.endingValue), toMoney(data.ruleOnly.endingValue)],
        ["PnL", toMoney(data.aiEnabled.pnl), toMoney(data.ruleOnly.pnl)]
    ];

    rows.forEach((row) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${row[0]}</td>
            <td class="mono">${row[1]}</td>
            <td class="mono">${row[2]}</td>
        `;
        body.appendChild(tr);
    });
}

async function loadActivity() {
    state.activityRows = await api("/api/strategies/activity");
    renderActivity();
}

async function loadStrategyReport() {
    state.strategyReportRows = await api("/api/strategies/report");
    renderStrategyReport();
}

function renderStrategyReport() {
    const body = $("strategyReportBody");
    body.innerHTML = "";

    for (const row of state.strategyReportRows) {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td class="mono">${escapeHtml(row.symbol)}</td>
            <td>${toNum(row.buyQty)}</td>
            <td>${toMoney(row.buyAmount)}</td>
            <td>${Number(row.buyTrades || 0)}</td>
            <td>${toNum(row.sellQty)}</td>
            <td>${toMoney(row.sellAmount)}</td>
            <td>${Number(row.sellTrades || 0)}</td>
        `;
        body.appendChild(tr);
    }
}

function renderActivity() {
    const rows = state.activityRows;
    const body = $("activityBody");
    const filter = $("activityFilter").value.trim();
    body.innerHTML = "";

    rows.forEach((row) => {
        const searchable = `${row.symbol} ${row.side} ${row.status} ${row.message || ""} ${row.orderId || ""}`;
        if (!includesFilter(searchable, filter)) {
            return;
        }
        const tr = document.createElement("tr");
        const statusClass = row.status === "success" ? "good" : row.status === "failed" ? "warn" : "";
        tr.innerHTML = `
            <td class="mono">${new Date(row.executedAt).toLocaleString()}</td>
            <td class="mono">${row.symbol}</td>
            <td>${row.side}</td>
            <td><span class="tag ${statusClass}">${row.status}</span></td>
            <td>${row.message || "-"}</td>
            <td class="mono">${row.orderId || "-"}</td>
        `;
        body.appendChild(tr);
    });
}

async function loadAccount() {
    const account = await api("/api/account");
    $("accountMode").textContent = account.mode;
    $("accountCash").textContent = toMoney(account.cash);
    $("accountEquity").textContent = toMoney(account.equity);
}

async function loadMarketConfig() {
    const config = await api("/api/market/config");
    $("marketProvider").textContent = config.provider;
    $("marketMaxStale").textContent = String(config.maxStaleSeconds);
}

async function loadHoldings() {
    const holdings = await api("/api/trade/positions");
    state.holdingsRows = [];

    for (const position of holdings) {
        const qty = Number(position.qty || 0);
        const avg = Number(position.averagePrice || 0);
        let currentPrice = null;
        try {
            const market = await api(`/api/market/latest?symbol=${encodeURIComponent(position.symbol)}&refresh=true`);
            currentPrice = market.price == null ? null : Number(market.price);
        } catch (error) {
            currentPrice = null;
        }

        const pnl = currentPrice == null ? null : (currentPrice - avg) * qty;
        state.holdingsRows.push({
            symbol: position.symbol,
            qty,
            avg,
            currentPrice,
            pnl
        });
    }

    renderHoldings();
}

async function saveManualHolding(event) {
    event.preventDefault();
    const symbol = $("holdingSymbol").value.trim().toUpperCase();
    const qty = Number($("holdingQty").value);
    const buyPrice = Number($("holdingBuyPrice").value);

    if (!symbol) {
        showToast("Holding symbol is required.", "error");
        return;
    }

    try {
        await api("/api/trade/positions", {
            method: "POST",
            body: JSON.stringify({ symbol, qty, buyPrice })
        });
        showToast(`Holding updated for ${symbol}.`);
        $("manualHoldingForm").reset();
        $("holdingQty").value = "1";
        await loadHoldings();
    } catch (error) {
        showError(error);
    }
}

function renderHoldings() {
    const body = $("holdingsBody");
    const filter = $("holdingsFilter").value.trim();
    body.innerHTML = "";

    for (const position of state.holdingsRows) {
        const searchable = `${position.symbol} ${position.qty} ${position.avg} ${position.currentPrice} ${position.pnl}`;
        if (!includesFilter(searchable, filter)) {
            continue;
        }
        const tr = document.createElement("tr");
        const currentPriceText = position.currentPrice == null ? "-" : toMoney(position.currentPrice);
        const pnlText = position.pnl == null ? "-" : toMoney(position.pnl);
        tr.innerHTML = `
            <td class="mono">${position.symbol}</td>
            <td>${toNum(position.qty)}</td>
            <td>${toMoney(position.avg)}</td>
            <td>${currentPriceText}</td>
            <td class="mono">${pnlText}</td>
            <td>
                <div class="inline-actions">
                    <button type="button" class="btn compact" data-holding-action="edit" data-symbol="${escapeHtml(position.symbol)}">Edit</button>
                    <button type="button" class="btn compact" data-holding-action="strategy" data-symbol="${escapeHtml(position.symbol)}">Add to Strategy</button>
                    <button type="button" class="btn compact" data-holding-action="delete" data-symbol="${escapeHtml(position.symbol)}">Delete</button>
                </div>
            </td>
        `;
        body.appendChild(tr);
    }
}

async function loadChart(strategyId) {
    if (!strategyId) {
        return;
    }
    const series = await api(`/api/strategies/${strategyId}/chart?limit=200`);
    const points = [...series.points].reverse();

    const labels = points.map((p) => new Date(p.capturedAt).toLocaleTimeString());
    const prices = points.map((p) => Number(p.price));
    const buy = points.map((p) => Number(p.buyTriggerPrice));
    const sell = points.map((p) => Number(p.sellTriggerPrice));

    if (series.latestQuoteAt) {
        const freshness = series.stale ? "stale" : "fresh";
        const age = series.latestAgeSeconds == null ? "n/a" : `${series.latestAgeSeconds}s`;
        $("chartMeta").textContent = `Latest quote at ${new Date(series.latestQuoteAt).toLocaleString()} (${age}, ${freshness}).`;
        const badge = $("chartFreshnessBadge");
        badge.textContent = freshness;
        badge.classList.remove("fresh", "stale");
        badge.classList.add(series.stale ? "stale" : "fresh");
    } else {
        $("chartMeta").textContent = "No quote metadata yet.";
        const badge = $("chartFreshnessBadge");
        badge.textContent = "unknown";
        badge.classList.remove("fresh", "stale");
    }

    const ctx = $("strategyChart");
    if (state.chart) {
        state.chart.destroy();
    }

    state.chart = new Chart(ctx, {
        type: "line",
        data: {
            labels,
            datasets: [
                {
                    label: "Market Price",
                    data: prices,
                    borderColor: "#5cc7ff",
                    backgroundColor: "rgba(92,199,255,0.15)",
                    borderWidth: 2,
                    tension: 0.25
                },
                {
                    label: `Buy Trigger (-${series.buyDropPercent}%)`,
                    data: buy,
                    borderColor: "#ffb35f",
                    borderWidth: 1.5,
                    borderDash: [6, 6],
                    pointRadius: 0,
                    tension: 0
                },
                {
                    label: `Sell Trigger (+${series.sellRisePercent}%)`,
                    data: sell,
                    borderColor: "#7fb0ff",
                    borderWidth: 1.5,
                    borderDash: [6, 6],
                    pointRadius: 0,
                    tension: 0
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    labels: {
                        color: "#d5e4ff"
                    }
                }
            },
            scales: {
                x: {
                    ticks: {
                        color: "#9fb6d8"
                    },
                    grid: {
                        color: "rgba(138,170,210,0.15)"
                    }
                },
                y: {
                    ticks: {
                        color: "#9fb6d8",
                        callback: (v) => `$${Number(v).toFixed(2)}`
                    },
                    grid: {
                        color: "rgba(138,170,210,0.15)"
                    }
                }
            }
        }
    });
}

async function refreshAll() {
    await Promise.all([loadStrategies(), loadActivity(), loadStrategyReport(), loadAccount(), loadMarketConfig()]);
    await loadAiSentimentByStrategy();
    await refreshAiDecision();
    await loadHoldings();
}

async function onRefreshAllClick() {
    const btn = $("refreshAllBtn");
    if (btn) {
        btn.disabled = true;
        btn.textContent = "Refreshing...";
    }
    try {
        await refreshAll();
        showToast("Dashboard refreshed.");
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = "Refresh";
        }
    }
}

function bind(id, event, handler) {
    const el = $(id);
    if (!el) {
        console.warn(`Missing element #${id} for ${event} binding`);
        return;
    }
    el.addEventListener(event, handler);
}

bind("strategyForm", "submit", saveStrategy);
bind("stockSearchForm", "submit", (e) => {
    e.preventDefault();
    searchStocks().catch(showError);
});
bind("manualHoldingForm", "submit", (e) => {
    saveManualHolding(e).catch(showError);
});
bind("aiDecisionForm", "submit", (e) => {
    e.preventDefault();
    refreshAiDecision().catch(showError);
});
bind("aiSymbolSelect", "change", () => {
    refreshAiDecision().catch(showError);
});
bind("backtestForm", "submit", (e) => {
    runBacktest(e).catch(showError);
});
bind("strategyTableBody", "click", (e) => {
    onStrategyTableClick(e).catch(showError);
});
bind("holdingsBody", "click", (e) => {
    onHoldingsTableClick(e).catch(showError);
});
bind("clearFormBtn", "click", clearForm);
bind("refreshAllBtn", "click", () => onRefreshAllClick().catch(showError));
bind("chartStrategySelect", "change", (e) => {
    loadChart(e.target.value).catch(showError);
});
bind("activityFilter", "input", renderActivity);
bind("holdingsFilter", "input", renderHoldings);
bind("stockSearchQuery", "keydown", (e) => {
    if (e.key === "Enter") {
        e.preventDefault();
        searchStocks().catch(showError);
    }
});

refreshAll().catch(showError);
setInterval(() => refreshAll().catch(showError), 30000);
