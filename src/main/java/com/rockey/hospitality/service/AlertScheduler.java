package com.rockey.hospitality.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertScheduler.class);
    private final AlertAutomationService automationService;

    public AlertScheduler(AlertAutomationService automationService) {
        this.automationService = automationService;
    }

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
