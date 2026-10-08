package com.rockey.hospitality.configuration;

import com.rockey.hospitality.security.JwtAuthenticationFilter;
import com.rockey.hospitality.security.SecurityHandlers.RestAccessDeniedHandler;
import com.rockey.hospitality.security.SecurityHandlers.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

/**
 * STUDY NOTE: Authentication asks who is calling; authorization asks what they may do.
 * At startup Spring builds this shared filter chain from the JWT filter, handlers and properties.
 * Each request then follows its ordered URL/method rules; a match allows or rejects route access.
 * The CORS bean uses an explicit browser-origin allowlist. CORS is not a login check.
 * Refresh also checks Origin in AuthController. Task ownership and state rules remain in services;
 * a route match alone cannot prove that this USER owns the requested task.
 */
@Configuration
public class SecurityConfiguration {
    private final JwtAuthenticationFilter jwt;
    private final RestAuthenticationEntryPoint unauthorized;
    private final RestAccessDeniedHandler forbidden;
    private final SecurityProperties properties;
    public SecurityConfiguration(JwtAuthenticationFilter jwt, RestAuthenticationEntryPoint unauthorized,
                                 RestAccessDeniedHandler forbidden, SecurityProperties properties) {
        this.jwt = jwt; this.unauthorized = unauthorized; this.forbidden = forbidden; this.properties = properties;
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .authorizeHttpRequests(access -> access
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/users").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/rooms/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/tasks", "/api/tasks/*").hasAnyRole("USER", "MANAGER", "ADMIN")
                        // Shared status route: service permits USER execution and supervisor cancellation only.
                        .requestMatchers(HttpMethod.PUT, "/api/tasks/*/status").hasAnyRole("USER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/tasks/**").hasAnyRole("MANAGER", "ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
