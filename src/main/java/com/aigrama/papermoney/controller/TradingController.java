package com.aigrama.papermoney.controller;

import com.aigrama.papermoney.dto.AccountDto;
import com.aigrama.papermoney.dto.ManualHoldingRequestDto;
import com.aigrama.papermoney.dto.OrderDto;
import com.aigrama.papermoney.dto.PlaceOrderRequestDto;
import com.aigrama.papermoney.dto.PositionDto;
import com.aigrama.papermoney.dto.UpdateAccountRequestDto;
import com.aigrama.papermoney.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HTTP endpoints for trading operations.
 */
@RestController
@Validated
@RequestMapping("/api")
public class TradingController {

    private final TradingService tradingService;

    public TradingController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/trade/orders")
    public Mono<OrderDto> placeOrder(@Valid @RequestBody PlaceOrderRequestDto request) {
        return tradingService.placeOrder(request);
    }

    @GetMapping("/trade/orders/{id}")
    public Mono<OrderDto> getOrder(@PathVariable("id") String id) {
        return tradingService.getOrder(id);
    }

    @DeleteMapping("/trade/orders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> cancelOrder(@PathVariable("id") String id) {
        return tradingService.cancelOrder(id);
    }

    @GetMapping("/trade/positions")
    public Flux<PositionDto> getPositions() {
        return tradingService.getPositions();
    }

    @PostMapping("/trade/positions")
    public Mono<PositionDto> upsertManualHolding(@Valid @RequestBody ManualHoldingRequestDto request) {
        return tradingService.upsertManualHolding(request);
    }

    @DeleteMapping("/trade/positions/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteManualHolding(@PathVariable("symbol") String symbol) {
        return tradingService.deleteManualHolding(symbol);
    }

    @GetMapping("/account")
    public Mono<AccountDto> getAccount() {
        return tradingService.getAccount();
    }

    @PutMapping("/account")
    public Mono<AccountDto> updateAccount(@RequestBody UpdateAccountRequestDto request) {
        return tradingService.updateAccount(request);
    }

    @PostMapping("/account")
    public Mono<AccountDto> updateAccountPost(@RequestBody UpdateAccountRequestDto request) {
        return tradingService.updateAccount(request);
    }
}
