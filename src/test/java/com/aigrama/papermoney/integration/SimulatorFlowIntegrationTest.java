package com.aigrama.papermoney.integration;

import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.aigrama.papermoney.repository.PositionRepository;
import com.aigrama.papermoney.repository.TradeRepository;
import com.aigrama.papermoney.service.TradingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end simulator flow test with default H2 profile.
 */
@SpringBootTest(properties = {
        "paperstock.mode=simulator",
    "paperstock.market-data.base-url=http://localhost:9",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SimulatorFlowIntegrationTest {

    @Autowired
    private TradingService tradingService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Test
    void shouldPlaceOrderAndCreateTradeAndPosition() {
        OrderDto order = tradingService.placeOrder(new PlaceOrderRequestDto(
                "AAPL",
                BigDecimal.ONE,
                "buy",
                "market",
                null
        )).block();

        assertEquals("filled", order.status());
        assertFalse(tradeRepository.findAll().isEmpty());
        assertFalse(positionRepository.findAll().isEmpty());
    }
}
