package com.rockey.hospitality.security;

import com.rockey.hospitality.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * STUDY NOTE: UserDetailsService is Spring Security's adapter for loading an account used in
 * authentication.
 * Here, @Service lets JwtAuthenticationFilter inject this database-backed loader.
 * UserRepository supplies the current account, and RockeyUserPrincipal exposes its current role and active
 * state to Spring Security.
 * Token claims alone are not the permanent source of account permissions.
 */
@Service
public class RockeyUserDetailsService implements UserDetailsService {

    // @Override shows that this method implements a superclass/interface contract rather than inventing a
    // separate hook.

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
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(RockeyUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
    }
}
