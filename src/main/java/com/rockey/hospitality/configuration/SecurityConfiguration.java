package com.rockey.hospitality.configuration;

import com.rockey.hospitality.security.JwtAuthenticationFilter;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.security.RestAccessDeniedHandler;
import com.rockey.hospitality.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final SecurityProperties securityProperties;

    public SecurityConfiguration(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            SecurityProperties securityProperties
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.securityProperties = securityProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml").hasRole(Role.ADMIN.name())
                        .requestMatchers("/v3/api-docs/**").denyAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/employees").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/employees/*")
                        .hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers("/api/employees/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/departments/**")
                        .hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers("/api/departments/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/rooms/**")
                        .hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/rooms/*/status")
                        .hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers("/api/rooms/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/tasks").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/tasks/assigned/*")
                        .hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/tasks/*")
                        .hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/complete")
                        .hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers("/api/tasks/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/events/registrations/me")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/events/*/registrations")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/events/*/registrations/me")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/events/**")
                        .hasAnyRole("USER", Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers("/api/events/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/inventory/**")
                        .hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers("/api/inventory/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/alerts/**").hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/alerts/*/read").hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/alerts/*").hasAnyRole(Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers("/api/alerts/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/analytics/dashboard")
                        .hasAnyRole("USER", Role.STAFF.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/analytics/rooms", "/api/analytics/tasks",
                                "/api/analytics/departments", "/api/analytics/inventory-events").hasRole(Role.ADMIN.name())
                        .requestMatchers("/api/analytics/**").denyAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
