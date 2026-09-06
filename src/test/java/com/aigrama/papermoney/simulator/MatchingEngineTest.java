package com.aigrama.papermoney.simulator;

import com.aigrama.papermoney.entity.MarketSnapshotEntity;
import com.aigrama.papermoney.entity.OrderEntity;
import com.aigrama.papermoney.entity.OrderSide;
import com.aigrama.papermoney.entity.OrderType;
import com.aigrama.papermoney.repository.MarketSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for matching and fee behavior.
 */
class MatchingEngineTest {

    @Test
    void marketOrderShouldPartiallyFillBasedOnLiquidity() {
        MarketSnapshotRepository repository = mock(MarketSnapshotRepository.class);
        when(repository.findTopBySymbolOrderByCapturedAtDesc("AAPL")).thenReturn(Optional.of(snapshot("AAPL", "100.00")));

        MatchingEngine engine = new MatchingEngine(repository, new BigDecimal("0.001"), new BigDecimal("2"));
        MatchingEngine.MatchResult result = engine.match(order("AAPL", "5", OrderType.MARKET, OrderSide.BUY, null));

        assertEquals(new BigDecimal("2"), result.filledQty());
        assertEquals(new BigDecimal("100.00"), result.fillPrice());
        assertEquals(new BigDecimal("0.200000"), result.fee());
        assertEquals(1, result.trades().size());
    }

    @Test
    void limitOrderShouldNotFillWhenPriceDoesNotMatch() {
        MarketSnapshotRepository repository = mock(MarketSnapshotRepository.class);
        when(repository.findTopBySymbolOrderByCapturedAtDesc("TSLA")).thenReturn(Optional.of(snapshot("TSLA", "250.00")));

        MatchingEngine engine = new MatchingEngine(repository, new BigDecimal("0.001"), new BigDecimal("10"));
        MatchingEngine.MatchResult result = engine.match(order("TSLA", "1", OrderType.LIMIT, OrderSide.BUY, "200"));

        assertEquals(BigDecimal.ZERO, result.filledQty());
        assertTrue(result.trades().isEmpty());
    }

    private MarketSnapshotEntity snapshot(String symbol, String price) {
        MarketSnapshotEntity entity = new MarketSnapshotEntity();
        entity.setId(UUID.randomUUID());
        entity.setSymbol(symbol);
        entity.setPrice(new BigDecimal(price));
        entity.setCapturedAt(LocalDateTime.now());
        return entity;
    }

    private OrderEntity order(String symbol, String qty, OrderType type, OrderSide side, String limitPrice) {
        OrderEntity entity = new OrderEntity();
        entity.setId(UUID.randomUUID());
        entity.setSymbol(symbol);
        entity.setQty(new BigDecimal(qty));
        entity.setType(type);
        entity.setSide(side);
        entity.setLimitPrice(limitPrice == null ? null : new BigDecimal(limitPrice));
        return entity;
    }
}
