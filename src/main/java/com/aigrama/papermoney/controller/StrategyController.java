package com.aigrama.papermoney.controller;

import com.aigrama.papermoney.dto.StrategyConfigDto;
import com.aigrama.papermoney.dto.StrategyConfigRequestDto;
import com.aigrama.papermoney.dto.StrategyExecutionDto;
import com.aigrama.papermoney.entity.StrategyExecutionEntity;
import com.aigrama.papermoney.repository.StrategyExecutionRepository;
import com.aigrama.papermoney.service.StrategyConfigService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API endpoints for strategy config and activity monitoring.
 */
@RestController
@Validated
@RequestMapping("/api/strategies")
public class StrategyController {

    private final StrategyConfigService strategyConfigService;
    private final StrategyExecutionRepository strategyExecutionRepository;

    public StrategyController(
            StrategyConfigService strategyConfigService,
            StrategyExecutionRepository strategyExecutionRepository
    ) {
        this.strategyConfigService = strategyConfigService;
        this.strategyExecutionRepository = strategyExecutionRepository;
    }

    @PostMapping
    public StrategyConfigDto create(@Valid @RequestBody StrategyConfigRequestDto request) {
        return strategyConfigService.create(request);
    }

    @GetMapping
    public List<StrategyConfigDto> list() {
        return strategyConfigService.list();
    }

    @PutMapping("/{id}")
    public StrategyConfigDto update(@PathVariable("id") String id, @Valid @RequestBody StrategyConfigRequestDto request) {
        return strategyConfigService.update(id, request);
    }

    @PostMapping("/{id}/pause")
    public StrategyConfigDto pause(@PathVariable("id") String id) {
        return strategyConfigService.pause(id);
    }

    @PostMapping("/{id}/resume")
    public StrategyConfigDto resume(@PathVariable("id") String id) {
        return strategyConfigService.resume(id);
    }

    @GetMapping("/activity")
    public List<StrategyExecutionDto> activity(@RequestParam(value = "symbol", required = false) String symbol) {
        List<StrategyExecutionEntity> executions = (symbol == null || symbol.isBlank())
                ? strategyExecutionRepository.findTop200ByOrderByExecutedAtDesc()
                : strategyExecutionRepository.findTop200BySymbolOrderByExecutedAtDesc(symbol.toUpperCase());
        return executions.stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}/executions")
    public List<StrategyExecutionDto> executions(@PathVariable("id") String strategyId) {
        return strategyExecutionRepository.findTop200ByStrategyConfigIdOrderByExecutedAtDesc(java.util.UUID.fromString(strategyId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private StrategyExecutionDto toDto(StrategyExecutionEntity execution) {
        return new StrategyExecutionDto(
                execution.getId().toString(),
                execution.getStrategyConfigId().toString(),
                execution.getSymbol(),
                execution.getSide().name().toLowerCase(),
                execution.getTriggerPrice(),
                execution.getReferencePrice(),
                execution.getOrderId(),
                execution.getStatus().name().toLowerCase(),
                execution.getMessage(),
                execution.getExecutedAt()
        );
    }
}
