package com.rockey.hospitality.security;

import com.rockey.hospitality.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Loads the database account used by Spring Security.
 * It supplies current account state instead of trusting a token as the permanent source of role and status.
 */
// Registers this business/security service for constructor injection.
@Service
public class RockeyUserDetailsService implements UserDetailsService {

    /**
     * Injected UserRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final UserRepository userRepository;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public RockeyUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Normalizes an email and looks up the current User, wrapping it as a security principal.
     * A missing account raises Spring Security's standard lookup failure.
     */
    // Implements the inherited Java/Spring contract rather than defining a separate callback.
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(RockeyUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
    }
}
