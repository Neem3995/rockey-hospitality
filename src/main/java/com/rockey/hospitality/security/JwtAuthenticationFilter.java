package com.rockey.hospitality.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates Bearer requests before protected controllers run.
 * A valid JWT alone is insufficient when the database account is disabled or its identity or role no longer matches.
 */
// Registers this class as a Spring-managed component discovered during startup.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Required Authorization header prefix checked before JWT parsing.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Injected JwtService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final JwtService jwtService;
    /**
     * Injected RockeyUserDetailsService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final RockeyUserDetailsService userDetailsService;
    /**
     * Shared 401 writer for missing or invalid authentication.
     */
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public JwtAuthenticationFilter(
            JwtService jwtService,
            RockeyUserDetailsService userDetailsService,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * Verifies a supplied Bearer JWT, reloads its User, and installs the current principal in the security context.
     * Missing headers continue to route authorization; invalid headers or mismatched accounts return 401.
     */
    // Implements the inherited Java/Spring contract rather than defining a separate callback.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            reject(request, response);
            return;
        }

        try {
            // Verify the signature/expiry before treating claims as an identity.
            AccessTokenClaims claims = jwtService.parseAccessToken(
                    authorization.substring(BEARER_PREFIX.length())
            );
            RockeyUserPrincipal principal = (RockeyUserPrincipal) userDetailsService
                    .loadUserByUsername(claims.getEmail());

            // The database is authoritative: reject the token if the account is now disabled or its id/role changed.
            if (!principal.isEnabled()
                    || !principal.getId().equals(claims.getUserId())
                    || principal.getRole() != claims.getRole()) {
                reject(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException
                 | IllegalArgumentException
                 | UsernameNotFoundException exception) {
            SecurityContextHolder.clearContext();
            reject(request, response);
        }
    }

    /**
     * Delegates invalid-access-token responses to the common authentication entry point without exposing token contents.
     */
    private void reject(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException, ServletException {
        authenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException("Invalid access token.")
        );
    }
}
