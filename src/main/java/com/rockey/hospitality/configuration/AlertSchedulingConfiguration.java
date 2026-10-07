package com.rockey.hospitality.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables periodic alert scans through Spring's scheduler; AlertScheduler delegates each scan to the transactional automation service.
 */
// Marks this class as Spring configuration supplying application beans.
@Configuration
// Enables processing of @Scheduled methods by Spring's scheduler.
@EnableScheduling
public class AlertSchedulingConfiguration { }
