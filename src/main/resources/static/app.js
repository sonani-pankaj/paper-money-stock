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
    const brokerSelect = $("strategyBroker");
    if (brokerSelect) brokerSelect.value = strategy.broker || "simulator";
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
        openStrategyModal();
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
            closeStrategyModal();
            await refreshAll();
        } catch (error) {
            showError(error);
        }
    }
}

function openStrategyModal() {
    const modal = $("strategyModal");
    if (modal) {
        modal.classList.remove("hidden");
    }
}

function closeStrategyModal() {
    const modal = $("strategyModal");
    if (modal) {
        modal.classList.add("hidden");
    }
}

function openSearchModal() {
    const modal = $("stockSearchModal");
    if (modal) {
        modal.classList.remove("hidden");
        const input = $("stockSearchQuery");
        if (input) {
            input.focus();
        }
    }
}

function closeSearchModal() {
    const modal = $("stockSearchModal");
    if (modal) {
        modal.classList.add("hidden");
    }
}

function clearForm() {
    $("strategyId").value = "";
    $("strategyForm").reset();
    $("active").checked = true;
    const brokerSelect = $("strategyBroker");
    if (brokerSelect) brokerSelect.value = "simulator";
    $("buyDropPercent").value = "5";
    $("sellRisePercent").value = "10";
    $("buyCashPercent").value = "10";
    $("sellPositionPercent").value = "25";
    $("maxOrdersPerDay").value = "2";
    $("cooldownMinutes").value = "30";
}

function startNewStrategyForSymbol(symbol) {
    clearForm();
    closeSearchModal();
    const normalized = String(symbol || "").toUpperCase();
    $("symbol").value = normalized;
    openStrategyModal();
}

function openStrategyFromHolding(symbol) {
    const normalized = String(symbol || "").toUpperCase();
    const existing = state.strategies.find((s) => s.symbol === normalized);
    if (existing) {
        fillForm(existing);
        showToast(`Loaded existing strategy for ${normalized}.`);
    } else {
        startNewStrategyForSymbol(normalized);
    }
    openStrategyModal();
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
        if ($("holdingMode")) $("holdingMode").value = row.mode || "simulator";
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
        broker: $("strategyBroker") ? $("strategyBroker").value : "simulator"
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
        closeStrategyModal();
        await refreshAll();
    } catch (error) {
        showError(error);
    }
}

function renderStrategies() {
    const body = $("strategyTableBody");
    const select = $("chartStrategySelect");
    const emptyState = $("strategiesEmpty");
    const tableWrap = $("strategiesTableWrap");
    const badge = $("strategiesBadge");
    body.innerHTML = "";
    select.innerHTML = "";

    // Update badge
    const activeCount = state.strategies.filter((s) => s.active).length;
    if (badge) {
        if (state.strategies.length === 0) {
            badge.textContent = "none";
        } else {
            badge.textContent = `${state.strategies.length} total · ${activeCount} active`;
        }
    }

    // Show/hide empty state
    if (emptyState) emptyState.hidden = state.strategies.length > 0;
    if (tableWrap) tableWrap.hidden = state.strategies.length === 0;

    state.strategies.forEach((s) => {
        const tr = document.createElement("tr");
        const statusClass = s.active ? "good" : "warn";
        const currentPriceText = s.currentPrice == null ? "-" : toMoney(s.currentPrice);
        const refPriceText = s.referencePrice == null ? "-" : toMoney(s.referencePrice);
        const buyTargetText = s.buyTriggerPrice == null ? "-" : `${toMoney(s.buyTriggerPrice)} (-${toNum(s.buyDropPercent)}%)`;
        const sellTargetText = s.sellTriggerPrice == null ? "-" : `${toMoney(s.sellTriggerPrice)} (+${toNum(s.sellRisePercent)}%)`;

        tr.innerHTML = `
            <td class="mono">${s.symbol}</td>
            <td class="mono">${currentPriceText}</td>
            <td class="mono">${refPriceText}</td>
            <td class="mono">${buyTargetText}</td>
            <td class="mono">${sellTargetText}</td>
            <td><span class="tag ${statusClass}">${s.active ? "active" : "paused"}</span></td>
            <td><span class="mode-tag">${escapeHtml(formatBrokerLabel(s.broker || "simulator"))}</span></td>
            <td>
                <div class="inline-actions">
                    <button type="button" data-id="${s.id}" data-action="edit" class="btn compact">Edit</button>
                    <button type="button" data-id="${s.id}" data-action="toggle" class="btn compact">${s.active ? "Pause" : "Resume"}</button>
                    <button type="button" data-id="${s.id}" data-action="delete" class="btn compact">Delete</button>
                </div>
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

    const market = await api(`/api/market/latest?symbol=${encodeURIComponent(symbol)}&refresh=false`);
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
    state.strategyReportRows = await api("/api/strategies/trade-history");
    renderStrategyReport();
}

function renderStrategyReport() {
    const body = $("strategyReportBody");
    body.innerHTML = "";

    if (!state.strategyReportRows || state.strategyReportRows.length === 0) {
        body.innerHTML = `<tr><td colspan="7" class="hint" style="text-align:center;padding:16px;">No trades yet — strategies will appear here after the first BUY or SELL executes.</td></tr>`;
        return;
    }

    for (const row of state.strategyReportRows) {
        const isBuy  = (row.side || "").toUpperCase() === "BUY";
        const typeBadge = isBuy
            ? `<span class="tag good" style="min-width:40px;text-align:center;">BUY</span>`
            : `<span class="tag warn" style="min-width:40px;text-align:center;">SELL</span>`;
        const timeStr = row.executedAt
            ? new Date(row.executedAt).toLocaleString([], {month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",second:"2-digit"})
            : "-";
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td class="mono" style="white-space:nowrap;">${timeStr}</td>
            <td class="mono">${escapeHtml(row.symbol)}</td>
            <td>${typeBadge}</td>
            <td><span class="mode-tag">${escapeHtml(formatBrokerLabel(row.broker || "simulator"))}</span></td>
            <td class="mono">${toMoney(row.price)}</td>
            <td class="mono">${toNum(row.qty)}</td>
            <td class="mono">${toMoney(row.amount)}</td>
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
    const modeSelect = $("accountModeSelect");
    if (modeSelect && document.activeElement !== modeSelect) {
        modeSelect.value = account.mode || "simulator";
    }
    const cashInput = $("accountCashInput");
    if (cashInput && document.activeElement !== cashInput) {
        cashInput.value = account.cash != null ? Number(account.cash) : 100000;
    }
    $("accountEquity").textContent = toMoney(account.equity);

    // Show Simulator Price Override panel only in simulator mode
    const simWrap = $("simPriceOverrideWrap");
    if (simWrap) {
        simWrap.hidden = (account.mode || "simulator") !== "simulator";
    }
}

async function updateAccountMode() {
    const modeSelect = $("accountModeSelect");
    if (!modeSelect) return;
    const newMode = modeSelect.value;
    try {
        const updated = await api("/api/account", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ mode: newMode })
        });
        const selectedText = modeSelect.options[modeSelect.selectedIndex] ? modeSelect.options[modeSelect.selectedIndex].text : newMode;
        showToast(`Account mode updated to ${selectedText}`, "success");
        await refreshAll();
    } catch (error) {
        showError(error);
    }
}

async function saveAccountCash() {
    const cashInput = $("accountCashInput");
    if (!cashInput) return;
    const cashVal = parseFloat(cashInput.value);
    if (isNaN(cashVal) || cashVal < 0) {
        showToast("Please enter a valid cash amount (>= 0)", "error");
        return;
    }
    try {
        const updated = await api("/api/account", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ cash: cashVal })
        });
        showToast(`Cash balance updated to ${toMoney(updated.cash)}`, "success");
        await refreshAll();
    } catch (error) {
        showError(error);
    }
}

async function loadMarketConfig() {
    const config = await api("/api/market/config");
    $("marketProvider").textContent = config.provider;
    $("marketMaxStale").textContent = String(config.maxStaleSeconds);
}

function formatModeLabel(mode) {
    if (!mode) return "Simulator";
    const map = {
        simulator: "Simulator",
        alpaca: "Alpaca",
        interactive_brokers: "IBKR",
        td_ameritrade: "TD Ameritrade",
        webull: "Webull",
        e_trade: "E*TRADE",
        robinhood: "Robinhood"
    };
    return map[mode.toLowerCase()] || mode;
}

/** Alias used for broker badge rendering in strategy table and report. */
const formatBrokerLabel = formatModeLabel;

async function loadHoldings() {
    const holdings = await api("/api/trade/positions");
    const rows = await Promise.all(
        holdings.map(async (position) => {
            const qty = Number(position.qty || 0);
            const avg = Number(position.averagePrice || 0);
            let currentPrice = null;
            try {
                const market = await api(`/api/market/latest?symbol=${encodeURIComponent(position.symbol)}&refresh=false`);
                currentPrice = market.price == null ? null : Number(market.price);
            } catch (error) {
                currentPrice = null;
            }

            const pnl = currentPrice == null ? null : (currentPrice - avg) * qty;
            return {
                symbol: position.symbol,
                mode: position.mode || "simulator",
                qty,
                avg,
                currentPrice,
                pnl
            };
        })
    );

    const uniqueMap = new Map();
    for (const row of rows) {
        uniqueMap.set(row.symbol.toUpperCase(), row);
    }

    state.holdingsRows = Array.from(uniqueMap.values());

    const totalEquity = state.holdingsRows.reduce((sum, row) => {
        const liveOrAvgPrice = row.currentPrice != null ? row.currentPrice : row.avg;
        return sum + (liveOrAvgPrice * row.qty);
    }, 0);
    if ($("accountEquity")) {
        $("accountEquity").textContent = toMoney(totalEquity);
    }

    // Update portfolio group badge
    const portfolioBadge = $("portfolioBadge");
    if (portfolioBadge) {
        portfolioBadge.textContent = `${state.holdingsRows.length} holding${state.holdingsRows.length !== 1 ? "s" : ""} · ${toMoney(totalEquity)} equity`;
    }

    // Update holdings card badge
    const holdingsBadge = $("holdingsBadge");
    if (holdingsBadge) {
        holdingsBadge.textContent = `${state.holdingsRows.length} position${state.holdingsRows.length !== 1 ? "s" : ""}`;
    }

    // Auto-expand portfolio group if there are holdings
    if (state.holdingsRows.length > 0) {
        const toggle = $("portfolioToggle");
        const body = $("portfolioBody");
        if (toggle && body && toggle.getAttribute("aria-expanded") === "false") {
            toggle.setAttribute("aria-expanded", "true");
            body.hidden = false;
        }
    }

    renderHoldings();
}

async function saveManualHolding(event) {
    event.preventDefault();
    const symbol = $("holdingSymbol").value.trim().toUpperCase();
    const mode = $("holdingMode") ? $("holdingMode").value : "simulator";
    const qty = Number($("holdingQty").value);
    const buyPrice = Number($("holdingBuyPrice").value);

    if (!symbol) {
        showToast("Holding symbol is required.", "error");
        return;
    }

    try {
        await api("/api/trade/positions", {
            method: "POST",
            body: JSON.stringify({ symbol, mode, qty, buyPrice })
        });
        showToast(`Holding updated for ${symbol}.`);
        $("manualHoldingForm").reset();
        $("holdingQty").value = "1";
        if ($("accountModeSelect") && $("holdingMode")) {
            $("holdingMode").value = $("accountModeSelect").value || "simulator";
        }
        // Close the add-holding form
        const addWrap = $("addHoldingFormWrap");
        const addBtn = $("toggleAddHoldingBtn");
        if (addWrap) addWrap.hidden = true;
        if (addBtn) addBtn.textContent = "+ Add Holding";
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
        const modeLabel = formatModeLabel(position.mode);
        const searchable = `${position.symbol} ${position.mode} ${modeLabel} ${position.qty} ${position.avg} ${position.currentPrice} ${position.pnl}`;
        if (!includesFilter(searchable, filter)) {
            continue;
        }
        const tr = document.createElement("tr");
        const currentPriceText = position.currentPrice == null ? "-" : toMoney(position.currentPrice);
        const pnlText = position.pnl == null ? "-" : toMoney(position.pnl);
        tr.innerHTML = `
            <td class="mono">${position.symbol}</td>
            <td><span class="mode-tag">${escapeHtml(modeLabel)}</span></td>
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
bind("openSearchModalBtn", "click", openSearchModal);
bind("closeSearchModalBtn", "click", closeSearchModal);
bind("closeStrategyModalBtn", "click", closeStrategyModal);
bind("cancelStrategyModalBtn", "click", closeStrategyModal);
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
bind("accountModeSelect", "change", () => {
    updateAccountMode().catch(showError);
});
bind("saveCashBtn", "click", () => {
    saveAccountCash().catch(showError);
});
bind("accountCashInput", "keydown", (e) => {
    if (e.key === "Enter") {
        e.preventDefault();
        saveAccountCash().catch(showError);
    }
});

// + New Strategy button (always visible in Strategies header)
bind("newStrategyBtn", "click", () => {
    clearForm();
    openStrategyModal();
});

// Collapsible section toggles
function wireToggle(toggleId, bodyId) {
    const toggle = $(toggleId);
    const body = $(bodyId);
    if (!toggle || !body) return;
    toggle.addEventListener("click", () => {
        const expanded = toggle.getAttribute("aria-expanded") === "true";
        toggle.setAttribute("aria-expanded", String(!expanded));
        body.hidden = expanded;
    });
}

wireToggle("portfolioToggle", "portfolioBody");
wireToggle("analyticsToggle", "analyticsBody");
wireToggle("reportToggle", "reportBody");

// + Add Holding toggle
bind("toggleAddHoldingBtn", "click", () => {
    const wrap = $("addHoldingFormWrap");
    const btn = $("toggleAddHoldingBtn");
    if (!wrap) return;
    const isHidden = wrap.hidden;
    wrap.hidden = !isHidden;
    if (btn) btn.textContent = isHidden ? "✕ Cancel" : "+ Add Holding";
    if (isHidden) {
        const firstInput = wrap.querySelector("input");
        if (firstInput) firstInput.focus();
    }
});

// Simulator price override form
const simPriceFormEl = $("simPriceForm");
if (simPriceFormEl) {
    simPriceFormEl.addEventListener("submit", async (e) => {
        e.preventDefault();
        const symbol = $("simPriceSymbol").value.trim().toUpperCase();
        const price  = Number($("simPriceValue").value);
        if (!symbol || price <= 0) return;

        try {
            const result = await api("/api/simulator/price", {
                method: "PUT",
                body: JSON.stringify({ symbol, price })
            });
            showToast(`✅ ${symbol} price set to ${toMoney(result.price)}`, "success");

            // Append to in-panel history log
            const hist = $("simPriceHistory");
            if (hist) {
                const row = document.createElement("div");
                row.className = "sim-price-entry";
                const ts = new Date(result.effectiveAt || Date.now()).toLocaleTimeString();
                row.innerHTML = `
                    <span class="mono">${escapeHtml(result.symbol)}</span>
                    <span class="sim-price-val">${toMoney(result.price)}</span>
                    <span class="sim-price-ts">${ts}</span>
                    <span class="hint" style="font-size:0.75rem;">— strategy will evaluate on next cycle</span>
                `;
                hist.prepend(row);
            }

            // Reset only price field, keep symbol for quick re-testing
            $("simPriceValue").value = "";
        } catch (err) {
            showError(err);
        }
    });
}

// Load trigger hints when the user types a symbol into the sim price form
let simSymbolTimer;
const simSymbolInput = $("simPriceSymbol");
if (simSymbolInput) {
    simSymbolInput.addEventListener("input", () => {
        clearTimeout(simSymbolTimer);
        const sym = simSymbolInput.value.trim().toUpperCase();
        if (!sym) {
            const hints = $("simTriggerHints");
            if (hints) hints.hidden = true;
            return;
        }
        simSymbolTimer = setTimeout(() => loadSimTriggerHints(sym), 350);
    });
}

async function loadSimTriggerHints(symbol) {
    try {
        const data = await api(`/api/simulator/triggers?symbol=${encodeURIComponent(symbol)}`);
        const hints = $("simTriggerHints");
        if (!hints) return;

        if (!data.buyTrigger && !data.sellTrigger) {
            hints.hidden = true;
            return;
        }

        $("simBuyTrigger").textContent  = data.buyTrigger  ? toMoney(data.buyTrigger)  : "—";
        $("simSellTrigger").textContent = data.sellTrigger ? toMoney(data.sellTrigger) : "—";
        $("simRefPrice").textContent    = data.referencePrice ? toMoney(data.referencePrice) : "—";
        hints.hidden = false;
    } catch (_) {
        const hints = $("simTriggerHints");
        if (hints) hints.hidden = true;
    }
}

// Reset Baseline button — re-anchors the reference price to the current stored market price
bind("resetBaselineBtn", "click", async () => {
    const sym = $("simPriceSymbol") ? $("simPriceSymbol").value.trim().toUpperCase() : "";
    if (!sym) { showToast("Enter a symbol first", "warn"); return; }

    // Fetch the real live price and use it as the new baseline
    try {
        const latest = await api(`/api/market/latest?symbol=${encodeURIComponent(sym)}&refresh=true`);
        if (!latest || !latest.price) { showToast("Could not fetch live price", "warn"); return; }

        const result = await api("/api/simulator/baseline", {
            method: "PUT",
            body: JSON.stringify({ symbol: sym, price: latest.price })
        });
        showToast(`✅ Baseline reset to ${toMoney(result.baselinePrice)} for ${sym}`, "success");
        loadSimTriggerHints(sym);
    } catch (err) {
        showError(err);
    }
});

const HELP_DATA = {
    account: {
        title: "Account Snapshot Help",
        body: `
            <p><strong>Overview:</strong> Displays your account balance, trading mode, and market data status in real time.</p>
            <h4>Fields & Controls Explained:</h4>
            <ul>
                <li><code>Mode</code>: Select your active trading mode (e.g. <em>Simulator (Paper)</em> for local paper trading, or live stock brokers such as <em>Alpaca</em>, <em>Interactive Brokers</em>, <em>TD Ameritrade</em>, <em>Webull</em>, <em>E*TRADE</em>, or <em>Robinhood</em>).</li>
                <li><code>Cash</code>: Editable liquid cash balance available to execute BUY orders. Enter any cash amount and click <strong>Save</strong>.</li>
                <li><code>Holdings Equity</code>: Total market value of all active stock positions held in your portfolio.</li>
                <li><code>Quotes Provider</code>: Active market data provider (e.g. <em>finnhub</em>) and maximum quote stale seconds.</li>
            </ul>
        `
    },
    search: {
        title: "Stock Search & Add Holdings Help",
        body: `
            <p><strong>Overview:</strong> Search for any US stock ticker or company name, view live prices, and easily start a strategy configuration.</p>
            <h4>Fields & Actions Explained:</h4>
            <ul>
                <li><code>Search Stock Input</code>: Enter a symbol (e.g. <em>AAPL</em>, <em>NVDA</em>) or company name and click <strong>Find</strong>.</li>
                <li><code>Results Table</code>: Shows matching Symbol, Company Name, Exchange, and Live Market Price.</li>
                <li><code>Fill Strategy</code>: Opens the Strategy Config modal pre-filled with the selected stock symbol.</li>
            </ul>
        `
    },
    holdings: {
        title: "Holdings and P&L Help",
        body: `
            <p><strong>Overview:</strong> Manage your current portfolio stock holdings, select execution modes, view purchase cost basis, and monitor live unrealized P&L.</p>
            <h4>Fields & Actions Explained:</h4>
            <ul>
                <li><code>Symbol</code>: Stock ticker of the holding.</li>
                <li><code>Mode</code>: Execution mode or broker managing this position (e.g. <em>Simulator</em>, <em>Alpaca</em>, <em>IBKR</em>, <em>TD Ameritrade</em>, <em>Webull</em>, <em>E*TRADE</em>, <em>Robinhood</em>).</li>
                <li><code>Qty</code>: Number of shares owned.</li>
                <li><code>Buy Price</code>: Your purchase cost basis per share ($). Used as the reference price for strategy rules!</li>
                <li><code>Current Live Price</code>: Latest real-time stock price fetched from the market data provider.</li>
                <li><code>Unrealized P&L</code>: Profit or loss on the position (<code>(Live Price - Buy Price) × Qty</code>).</li>
                <li><code>Add/Update Holding Form</code>: Manually add new positions or adjust share qty, cost basis & execution mode.</li>
                <li><code>Add to Strategy</code>: Opens Strategy Config modal to set auto-buy and auto-sell rules for this holding.</li>
            </ul>
        `
    },
    strategies: {
        title: "Active Strategies Help",
        body: `
            <p><strong>Overview:</strong> Monitor all active stock trading strategies and their live price trigger thresholds.</p>
            <h4>Fields & Actions Explained:</h4>
            <ul>
                <li><code>Symbol</code>: Stock ticker monitored by the strategy.</li>
                <li><code>Live Price</code>: Current market price of the stock.</li>
                <li><code>Ref Price</code>: Reference price used for rule calculation (Cost basis from Holdings, or initial market price).</li>
                <li><code>Buy Target (Drop%)</code>: Target price that triggers an automated BUY (e.g., 5% drop below Ref Price).</li>
                <li><code>Sell Target (Rise%)</code>: Target price that triggers an automated SELL (e.g., 10% rise above Ref Price).</li>
                <li><code>Status</code>: <em>Active</em> (evaluating automatically) or <em>Paused</em>.</li>
                <li><code>Modes</code>: Enabled execution adapters (e.g. <em>sim</em> for simulator, <em>alpaca</em> for broker).</li>
                <li><code>Edit / Pause / Delete</code>: Edit strategy parameters, pause/resume execution, or delete the strategy.</li>
            </ul>
        `
    },
    report: {
        title: "Strategy Buy/Sell Report Help",
        body: `
            <p><strong>Overview:</strong> Displays aggregated execution metrics from all successful automated trades, grouped by stock symbol.</p>
            <h4>Fields Explained:</h4>
            <ul>
                <li><code>Symbol</code>: Stock ticker for the report summary.</li>
                <li><code>Buy Qty / Buy Amount / Buy Trades</code>: Total shares purchased, total cash spent, and count of BUY executions.</li>
                <li><code>Sell Qty / Sell Amount / Sell Trades</code>: Total shares sold, total cash received, and count of SELL executions.</li>
            </ul>
        `
    },
    ai: {
        title: "AI Insights & One-Click Backtest Help",
        body: `
            <p><strong>Overview:</strong> View real-time AI sentiment analysis, dynamic volatility adjustments, LIVE AI Decision criteria, and run historical side-by-side strategy simulations.</p>
            <h4>Sections & LIVE AI Decision Fields Explained:</h4>
            <ul>
                <li><code>Current Sentiment Score</code>: Aggregated news & market sentiment score ranging from -1.0 (Bearish) to +1.0 (Bullish).</li>
                <li><code>Current Live Price</code>: Real-time stock market price evaluated by the AI decision engine.</li>
                <li><code>Buy Probability</code>: AI model confidence score (0% - 100%) evaluating likelihood of a profitable buy entry. Must exceed the probability threshold (e.g. 55%) to pass.</li>
                <li><code>Sell Probability</code>: AI model confidence score (0% - 100%) evaluating likelihood of profit-taking or exit.</li>
                <li><code>Dynamic Buy Drop%</code>: Volatility-adjusted buy drop threshold. Scales your base drop % dynamically based on historical price volatility.</li>
                <li><code>Dynamic Sell Rise%</code>: Volatility-adjusted sell rise threshold. Scales your base rise % dynamically based on price volatility.</li>
                <li><code>BUY / SELL Gate</code>: Gating decision status (<code>BUY yes/no / SELL yes/no</code>). Shows whether AI signal thresholds and sentiment requirements allow trade execution.</li>
                <li><code>One-Click Backtest (AI vs Rule-Only)</code>: Simulates your strategy on past price data comparing <strong>AI-Enabled</strong> vs <strong>Rule-Only</strong> results (Ending Cash, Qty, Total Value, and Net P&L).</li>
            </ul>
        `
    },
    chart: {
        title: "Price vs Trigger Lines Help",
        body: `
            <p><strong>Overview:</strong> Interactive line chart visualizing historical price movements alongside active BUY and SELL trigger target lines for any strategy.</p>
            <h4>Fields Explained:</h4>
            <ul>
                <li><code>Selected Strategy</code>: Choose which stock strategy chart to visualize.</li>
                <li><code>Market Price Line</code>: Real-time price snapshot history.</li>
                <li><code>Buy Trigger Line</code>: Dynamic or static threshold price required for a BUY.</li>
                <li><code>Sell Trigger Line</code>: Dynamic or static threshold price required for a SELL.</li>
                <li><code>Freshness Badge</code>: Indicates if quotes are <em>fresh</em> or <em>stale</em>.</li>
            </ul>
        `
    },
    activity: {
        title: "Automation Activity Audit Log Help",
        body: `
            <p><strong>Overview:</strong> Real-time audit log of every strategy evaluation cycle, order execution, skipped signal, or error.</p>
            <h4>Fields Explained:</h4>
            <ul>
                <li><code>Time</code>: Timestamp of the evaluation cycle.</li>
                <li><code>Symbol & Side</code>: Stock ticker and order direction (BUY or SELL).</li>
                <li><code>Status</code>: <code>success</code> (order placed), <code>skipped</code> (threshold not crossed / AI gated / cooldown), or <code>failed</code>.</li>
                <li><code>Message & Order</code>: Detailed reason or returned broker Order ID.</li>
            </ul>
        `
    }
};

function openHelpModal(cardKey) {
    const helpInfo = HELP_DATA[cardKey];
    if (!helpInfo) {
        return;
    }
    const titleEl = $("helpModalTitle");
    const bodyEl = $("helpModalBody");
    const modalEl = $("helpModal");

    if (titleEl && bodyEl && modalEl) {
        titleEl.textContent = helpInfo.title;
        bodyEl.innerHTML = helpInfo.body;
        modalEl.classList.remove("hidden");
    }
}

function closeHelpModal() {
    const modalEl = $("helpModal");
    if (modalEl) {
        modalEl.classList.add("hidden");
    }
}

bind("closeHelpModalBtn", "click", closeHelpModal);

document.addEventListener("click", (e) => {
    const helpBtn = e.target.closest(".help-btn");
    if (helpBtn && helpBtn.dataset.help) {
        openHelpModal(helpBtn.dataset.help);
    }
});

refreshAll().catch(showError);
setInterval(() => refreshAll().catch(showError), 60000);
