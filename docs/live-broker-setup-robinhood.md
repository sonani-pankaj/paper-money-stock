# Activating Live Stock Broker Integration (Example: Robinhood)

This guide explains how to connect and activate live stock brokers (such as **Robinhood**, **Alpaca**, **Interactive Brokers**, **TD Ameritrade**, **Webull**, or **E*TRADE**) in the Paper Stock application.

---

## 1. Overview of Broker Modes

Paper Stock supports multi-adapter execution environments:
- **Simulator (Paper)**: Local paper trading engine simulating fills against live market price feeds without risking real money.
- **Live Stock Brokers**: Connects automated strategy orders directly to live brokerage accounts via REST / WebSocket APIs.

You can select your active broker globally from **Account Snapshot** or assign specific broker modes per holding in **Holdings and P&L**.

---

## 2. Step 1: Selecting Robinhood Mode in the UI

1. Open the application dashboard in your browser (`http://localhost:8080`).
2. Locate the **Account Snapshot** panel at the top left.
3. Click the **Mode** dropdown selector and choose **Robinhood** (or your target broker).
4. Click **Save** next to your Cash balance if you want to initialize or override your available liquid cash.
5. In **Holdings and P&L**, when adding or editing a position, select **Robinhood** from the **Mode** dropdown to tag that holding for Robinhood execution.

---

## 3. Step 2: Configuring Broker API Credentials

Live brokers require API key authentication and secure environment variables.

### Environment Variables setup (`application.yml`)

Add your Robinhood API credentials in `src/main/resources/application.yml` or export them as environment variables:

```yaml
paperstock:
  mode: ${PAPERSTOCK_MODE:robinhood}
  robinhood:
    base-url: ${ROBINHOOD_BASE_URL:https://api.robinhood.com}
    api-key: ${ROBINHOOD_API_KEY:}
    private-key: ${ROBINHOOD_PRIVATE_KEY:}
    account-number: ${ROBINHOOD_ACCOUNT_NUMBER:}
```

### Exporting in Shell:

```bash
export PAPERSTOCK_MODE="robinhood"
export ROBINHOOD_API_KEY="your_robinhood_api_key"
export ROBINHOOD_PRIVATE_KEY="your_base64_encoded_ed25519_private_key"
export ROBINHOOD_ACCOUNT_NUMBER="your_account_number"
```

---

## 4. Step 3: Implementing the Robinhood Trading Adapter

The application uses the `TradingAdapter` interface to delegate order execution. To support Robinhood live trades, create a dedicated adapter class `RobinhoodAdapter.java`:

### File Path: `src/main/java/com/aigrama/papermoney/adapter/RobinhoodAdapter.java`

```java
package com.aigrama.papermoney.adapter;

import com.aigrama.papermoney.dto.AccountDto;
import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.aigrama.papermoney.dto.PositionDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Robinhood live brokerage trading implementation.
 */
@Component("robinhoodAdapter")
public class RobinhoodAdapter implements TradingAdapter {

    private final WebClient robinhoodWebClient;

    public RobinhoodAdapter(@Qualifier("robinhoodWebClient") WebClient robinhoodWebClient) {
        this.robinhoodWebClient = robinhoodWebClient;
    }

    @Override
    public Mono<OrderDto> placeOrder(PlaceOrderRequestDto request) {
        // Construct Robinhood /orders/ payload and sign request headers
        return robinhoodWebClient.post()
                .uri("/orders/")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OrderDto.class);
    }

    @Override
    public Mono<OrderDto> getOrder(String orderId) {
        return robinhoodWebClient.get()
                .uri("/orders/" + orderId + "/")
                .retrieve()
                .bodyToMono(OrderDto.class);
    }

    @Override
    public Mono<Void> cancelOrder(String orderId) {
        return robinhoodWebClient.post()
                .uri("/orders/" + orderId + "/cancel/")
                .retrieve()
                .bodyToMono(Void.class);
    }

    @Override
    public Flux<PositionDto> getPositions() {
        return robinhoodWebClient.get()
                .uri("/positions/")
                .retrieve()
                .bodyToFlux(PositionDto.class);
    }

    @Override
    public Mono<AccountDto> getAccount() {
        return robinhoodWebClient.get()
                .uri("/accounts/")
                .retrieve()
                .bodyToMono(AccountDto.class);
    }
}
```

---

## 5. Step 4: Live Order Execution & Risk Controls

When automated strategy rules (e.g. 5% price drop or 10% price rise) trigger a BUY or SELL signal:
1. `AutomatedStrategyService` checks the active strategy symbol and account mode.
2. If mode is **Robinhood**, `TradingService` delegates the order to `RobinhoodAdapter`.
3. The order is submitted to Robinhood's API endpoint.
4. Execution results, order IDs, and filled prices are recorded in the **Automation Activity Audit Log**.

---

## 6. Safety Verification Checklist

Before running live automated strategies on a real brokerage account:
1. **Test in Simulator Mode**: First verify strategy thresholds (Drop% / Rise%) using `Simulator (Paper)` mode.
2. **Backtest Strategy**: Use the **One-Click Backtest (AI vs Rule-Only)** tool to simulate historical performance.
3. **Verify API Permissions**: Ensure your API key has trading permissions enabled and IP whitelist rules configured.
4. **Monitor Audit Log**: Check the **Automation Activity Audit Log** card to verify live execution statuses.
