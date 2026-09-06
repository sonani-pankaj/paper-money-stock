package com.aigrama.papermoney.adapter;

import com.aigrama.papermoney.dto.AccountDto;
import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.aigrama.papermoney.dto.PositionDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Backend contract for trading operations.
 */
public interface TradingAdapter {
    Mono<OrderDto> placeOrder(PlaceOrderRequestDto req);

    Mono<OrderDto> getOrder(String orderId);

    Mono<Void> cancelOrder(String orderId);

    Flux<PositionDto> getPositions();

    Mono<AccountDto> getAccount();
}
