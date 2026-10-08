package com.rockey.hospitality.security;

import com.rockey.hospitality.security.JwtService;
import com.rockey.hospitality.security.SecurityHandlers.RestAuthenticationEntryPoint;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * STUDY NOTE: A Filter runs before protected controllers; authentication verifies identity and
 * authorization decides access.
 * Here, @Component lets SecurityConfiguration inject this filter into Spring Security's request chain.
 * It verifies the signed Bearer access JWT with JwtService, then reloads the User through
 * UserDetailsService.
 * Current database ID, role and active state remain authoritative before a principal is placed in the
 * security context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // @Override shows that this method implements a superclass/interface contract rather than inventing a
    // separate hook.

    /**
     * Required Authorization header prefix checked before JWT parsing.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Injected JwtService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final JwtService jwtService;
    /**
     * Injected UserDetailsService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final UserDetailsService userDetailsService;
    /**
     * Shared 401 writer for missing or invalid authentication.
     */
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
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
            JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(
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
