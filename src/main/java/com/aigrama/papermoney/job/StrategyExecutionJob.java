package com.aigrama.papermoney.job;

import com.aigrama.papermoney.service.AutomatedStrategyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polling job that evaluates active stock strategies.
 */
@Component
@ConditionalOnProperty(name = "paperstock.strategy.enabled", havingValue = "true", matchIfMissing = true)
public class StrategyExecutionJob {

    private final AutomatedStrategyService automatedStrategyService;

    public StrategyExecutionJob(AutomatedStrategyService automatedStrategyService) {
        this.automatedStrategyService = automatedStrategyService;
    }

    @Scheduled(fixedDelayString = "${paperstock.strategy.eval-ms:30000}")
    public void evaluate() {
        automatedStrategyService.evaluateAllActive();
    }
}
