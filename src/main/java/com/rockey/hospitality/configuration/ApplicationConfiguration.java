package com.rockey.hospitality.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * Provides shared time and password-hashing dependencies for the services rather than constructing them in each caller.
 */
// Marks this class as Spring configuration supplying application beans.
@Configuration
public class ApplicationConfiguration {

    /**
     * Provides the shared UTC Clock for injectable time calculations.
     * Services needing server-local LocalDateTime explicitly choose that zone.
     */
    // Registers this method's returned object as a shared Spring-managed dependency.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Provides BCrypt encoding and matching for passwords.
     * A stored hash is compared through PasswordEncoder rather than decrypted.
     */
    // Registers this method's returned object as a shared Spring-managed dependency.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
