package com.aigrama.papermoney.simulator;

import com.aigrama.papermoney.entity.OrderEntity;
import com.aigrama.papermoney.entity.OrderSide;
import com.aigrama.papermoney.entity.OrderType;
import com.aigrama.papermoney.entity.TradeEntity;
import com.aigrama.papermoney.repository.MarketSnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Local matching engine for simulator mode.
 */
@Component
public class MatchingEngine {

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final BigDecimal feePercentage;
    private final BigDecimal availableLiquidity;

    public MatchingEngine(
            MarketSnapshotRepository marketSnapshotRepository,
            @Value("${paperstock.fee-percentage:0.001}") BigDecimal feePercentage,
            @Value("${paperstock.available-liquidity:100}") BigDecimal availableLiquidity
    ) {
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.feePercentage = feePercentage;
        this.availableLiquidity = availableLiquidity;
    }

    /**
     * Matches an incoming order using latest persisted market snapshot.
     */
    public MatchResult match(OrderEntity order) {
        Optional<BigDecimal> priceOpt = marketSnapshotRepository
                .findTopBySymbolOrderByCapturedAtDesc(order.getSymbol().toUpperCase())
                .map(snapshot -> snapshot.getPrice());

        if (priceOpt.isEmpty()) {
            return new MatchResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        BigDecimal marketPrice = priceOpt.get();
        if (!isFillable(order, marketPrice)) {
            return new MatchResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        BigDecimal fillQty = order.getQty().min(availableLiquidity);
        if (fillQty.compareTo(BigDecimal.ZERO) <= 0) {
            return new MatchResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        BigDecimal fee = fillQty.multiply(marketPrice).multiply(feePercentage).setScale(6, RoundingMode.HALF_UP);

        TradeEntity trade = new TradeEntity();
        trade.setId(UUID.randomUUID());
        trade.setOrder(order);
        trade.setSymbol(order.getSymbol().toUpperCase());
        trade.setQty(fillQty);
        trade.setPrice(marketPrice);
        trade.setFee(fee);
        trade.setTradedAt(LocalDateTime.now());

        return new MatchResult(fillQty, marketPrice, fee, List.of(trade));
    }

    private boolean isFillable(OrderEntity order, BigDecimal marketPrice) {
        if (order.getType() == OrderType.MARKET) {
            return true;
        }
        if (order.getLimitPrice() == null) {
            return false;
        }
        if (order.getSide() == OrderSide.BUY) {
            return marketPrice.compareTo(order.getLimitPrice()) <= 0;
        }
        return marketPrice.compareTo(order.getLimitPrice()) >= 0;
    }

    /**
     * Match outcome for persistence and DTO mapping.
     */
    public record MatchResult(
            BigDecimal filledQty,
            BigDecimal fillPrice,
            BigDecimal fee,
            List<TradeEntity> trades
    ) {
    }
}
