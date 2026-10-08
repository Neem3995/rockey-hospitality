package com.rockey.hospitality;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * STUDY NOTE: This is where we start the backend, before any browser request arrives.
 * main receives launch arguments and SpringApplication.run builds the application context and HTTP server.
 * The @SpringBootApplication annotation finds our configuration and components under this package.
 * Spring wires those shared objects together; it does not rebuild them for each request.
 * This entry point starts the app. Services, not main, decide housekeeping rules.
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
