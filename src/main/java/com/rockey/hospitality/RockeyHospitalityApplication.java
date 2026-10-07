package com.rockey.hospitality;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the Spring Boot backend.
 * Component scanning begins in this package so controllers, services, repositories, and configuration below it can be discovered.
 */
// Combines Boot configuration, automatic configuration, and component scanning from this package.
@SpringBootApplication
public class RockeyHospitalityApplication {

    /**
     * Hands startup to SpringApplication, which creates the application context and starts the configured embedded web server.
     */
    public static void main(String[] args) {
        SpringApplication.run(RockeyHospitalityApplication.class, args);
    }
}
