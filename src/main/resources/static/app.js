const $ = (id) => document.getElementById(id);

const state = {
    strategies: [],
    chart: null,
    activityRows: [],
    holdingsRows: []
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
    alert(`Request failed: ${error.message}`);
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
    const id = $("strategyId").value;
    const payload = strategyPayloadFromForm();

    try {
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
                <button data-id="${s.id}" data-action="edit" class="btn">Edit</button>
                <button data-id="${s.id}" data-action="toggle" class="btn">${s.active ? "Pause" : "Resume"}</button>
                <button data-id="${s.id}" data-action="delete" class="btn">Delete</button>
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

    body.querySelectorAll("button[data-action='edit']").forEach((btn) => {
        btn.addEventListener("click", () => {
            const strategy = state.strategies.find((s) => s.id === btn.dataset.id);
            if (strategy) {
                fillForm(strategy);
            }
        });
    });

    body.querySelectorAll("button[data-action='toggle']").forEach((btn) => {
        btn.addEventListener("click", async () => {
            try {
                const strategy = state.strategies.find((s) => s.id === btn.dataset.id);
                if (!strategy) {
                    return;
                }
                const path = strategy.active ? "pause" : "resume";
                await api(`/api/strategies/${strategy.id}/${path}`, { method: "POST" });
                await refreshAll();
            } catch (error) {
                showError(error);
            }
        });
    });

    body.querySelectorAll("button[data-action='delete']").forEach((btn) => {
        btn.addEventListener("click", async () => {
            try {
                const strategy = state.strategies.find((s) => s.id === btn.dataset.id);
                if (!strategy) {
                    return;
                }
                if (!confirm(`Delete strategy for ${strategy.symbol}?`)) {
                    return;
                }
                await api(`/api/strategies/${strategy.id}`, { method: "DELETE" });
                clearForm();
                await refreshAll();
            } catch (error) {
                showError(error);
            }
        });
    });
}

async function loadStrategies() {
    state.strategies = await api("/api/strategies");
    renderStrategies();
}

async function loadActivity() {
    state.activityRows = await api("/api/strategies/activity");
    renderActivity();
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

async function loadHoldings() {
    const holdings = await api("/api/trade/positions");
    state.holdingsRows = [];

    for (const position of holdings) {
        const market = await api(`/api/market/latest?symbol=${encodeURIComponent(position.symbol)}`);
        const currentPrice = Number(market.price || 0);
        const qty = Number(position.qty || 0);
        const avg = Number(position.averagePrice || 0);
        const pnl = (currentPrice - avg) * qty;
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
        tr.innerHTML = `
            <td class="mono">${position.symbol}</td>
            <td>${toNum(position.qty)}</td>
            <td>${toMoney(position.avg)}</td>
            <td>${toMoney(position.currentPrice)}</td>
            <td class="mono">${toMoney(position.pnl)}</td>
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
    await Promise.all([loadStrategies(), loadActivity(), loadAccount()]);
    await loadHoldings();
}

$("strategyForm").addEventListener("submit", saveStrategy);
$("clearFormBtn").addEventListener("click", clearForm);
$("refreshAllBtn").addEventListener("click", () => refreshAll().catch(showError));
$("chartStrategySelect").addEventListener("change", (e) => {
    loadChart(e.target.value).catch(showError);
});
$("activityFilter").addEventListener("input", renderActivity);
$("holdingsFilter").addEventListener("input", renderHoldings);

refreshAll().catch(showError);
setInterval(() => refreshAll().catch(showError), 30000);
