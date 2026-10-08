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
 * STUDY NOTE: Authentication identifies a user; authorization limits that identity's actions.
 * @Configuration/@Bean build the stateless security chain and explicit credentialed CORS allowlist.
 * JWT verification reloads active/role state from MySQL; services additionally enforce task ownership.
 * Bearer APIs do not use CSRF tokens; refresh validates Origin and retains HttpOnly/SameSite controls.
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
