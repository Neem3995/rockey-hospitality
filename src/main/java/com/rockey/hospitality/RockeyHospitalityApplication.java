package com.rockey.hospitality;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * STUDY NOTE: This is the entry point that starts the Spring Boot backend.
 * Here, @SpringBootApplication combines configuration, automatic framework setup and component scanning below
 * this package.
 * SpringApplication creates Spring's application context (its managed objects) and starts the embedded HTTP
 * server.
 * The discovered controllers, services, repositories and configuration then work together to handle
 * requests.
 */
@SpringBootApplication
public class RockeyHospitalityApplication {

    /**
     * Hands startup to SpringApplication, which creates the application context and starts the configured embedded web server.
     */
    public static void main(String[] args) {
        SpringApplication.run(RockeyHospitalityApplication.class, args);
    }
}
