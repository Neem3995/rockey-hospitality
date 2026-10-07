package com.rockey.hospitality.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * STUDY NOTE: A scheduler triggers work automatically rather than waiting for an HTTP request.
 * Here, @Component lets Spring manage this class, and @Scheduled requests a fixed delay after each scan finishes.
 * ApplicationConfiguration enables scheduling; this class delegates database work to
 * AlertAutomationService.
 * Failed scans log only the failure type and leave the next scheduled attempt available.
 */
@Component
public class AlertScheduler {

    /**
     * Class logger used for safe diagnostics without credential or token values.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertScheduler.class);
    /**
     * Injected AlertAutomationService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final AlertAutomationService automationService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public AlertScheduler(AlertAutomationService automationService) {
        this.automationService = automationService;
    }

    /**
     * Calls the transactional automation service and catches scan failures at the scheduler boundary.
     * Fixed delay starts the next scan after this call finishes.
     */
    // Runs after a configurable fixed delay, defaulting to five minutes after completion; the initial delay uses the same setting.
    @Scheduled(fixedDelayString = "${rockey.alerts.scan-delay-ms:300000}",
               initialDelayString = "${rockey.alerts.scan-delay-ms:300000}")
    public void scan() {
        try {
            automationService.runChecks();
        } catch (RuntimeException exception) {
            // The service transaction has rolled back; log safely and permit the next scheduled scan.
            LOGGER.error("Alert automation scan failed: {}", exception.getClass().getSimpleName());
        }
    }
}
