package com.rockey.hospitality.configuration;

import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.security.JwtAuthenticationFilter;
import com.rockey.hospitality.security.SecurityHandlers.RestAccessDeniedHandler;
import com.rockey.hospitality.security.SecurityHandlers.RestAuthenticationEntryPoint;
import java.util.List;
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

/**
 * STUDY NOTE: Authentication verifies who a caller is; authorization decides what that caller may access.
 * Here, @Configuration supplies framework setup, and @Bean registers the security filter chain and browser-origin
 * policy.
 * JwtAuthenticationFilter checks identity before controller access; route roles and service ownership
 * checks protect the data.
 * CORS restricts browser origins, and AuthController separately checks refresh-request Origin against the
 * same allowlist.
 */
@Configuration
public class SecurityConfiguration {

    // A Spring bean is an object managed by the application context and injected into collaborating
    // components.
    // CORS controls browser cross-origin reads; it is not a replacement for authentication or service
    // permissions.
    // Bearer routes are stateless; refresh has narrow Origin validation plus configured
    // HttpOnly/SameSite/Secure cookie controls.

    /**
     * JWT filter placed before protected route authorization.
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    /**
     * Shared 401 writer for missing or invalid authentication.
     */
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    /**
     * Shared 403 writer for authenticated callers lacking permission.
     */
    private final RestAccessDeniedHandler accessDeniedHandler;
    /**
     * Validated external security configuration shared with token and cookie handling.
     */
    private final SecurityProperties securityProperties;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
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

    /**
     * Builds the stateless security chain in matcher order and runs JWT authentication before the username/password filter.
     * Route gates establish roles; service methods enforce ownership.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Backend role gates protect routes; services still enforce ownership and eligibility.
        http
                // Bearer APIs do not use CSRF tokens; AuthController separately validates a present refresh Origin against the CORS allowlist.
                .csrf(csrf -> csrf.disable())
                // Browser origin restrictions and credential support come from the same explicit security configuration.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Do not create an HTTP login session; Bearer authentication is rebuilt for each request.
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                // Use safe JSON 401/403 handlers instead of login redirects or HTML errors.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // The first matching rule wins; specific read/action routes must precede broader management rules.
                .authorizeHttpRequests(authorize -> authorize
                        // Only ADMIN can read the published JSON/YAML contract; other documentation paths below are denied.
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml").hasRole(User.Role.ADMIN.name())
                        .requestMatchers("/v3/api-docs/**").denyAll()
                        // Registration and login are public; refresh uses its cookie and Origin checks instead of a Bearer token.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh"
                        ).permitAll()
                        // Profile and logout require the identity established by the access JWT.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        // ADMIN manages Employee lists and writes; STAFF detail reads still require its own linked profile in the service.
                        .requestMatchers(HttpMethod.GET, "/api/employees").hasRole(User.Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/employees/*")
                        .hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers("/api/employees/**").hasRole(User.Role.ADMIN.name())
                        // Operational roles may read Departments; only ADMIN can change them.
                        .requestMatchers(HttpMethod.GET, "/api/departments/**")
                        .hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers("/api/departments/**").hasRole(User.Role.ADMIN.name())
                        // STAFF can read active Rooms and request eligible status transitions; Room management writes remain ADMIN-only.
                        .requestMatchers(HttpMethod.GET, "/api/rooms/**")
                        .hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/rooms/*/status")
                        .hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers("/api/rooms/**").hasRole(User.Role.ADMIN.name())
                        // ADMIN searches/manages Tasks; specific STAFF read/completion routes remain subject to assignee ownership.
                        .requestMatchers(HttpMethod.GET, "/api/tasks").hasRole(User.Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/tasks/assigned/*")
                        .hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/tasks/*")
                        .hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/complete")
                        .hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers("/api/tasks/**").hasRole(User.Role.ADMIN.name())
                        // Self-registration routes are USER-only and precede general Event reads. Event management writes remain ADMIN-only.
                        .requestMatchers(HttpMethod.GET, "/api/events/registrations/me")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/events/*/registrations")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/events/*/registrations/me")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/events/**")
                        .hasAnyRole("USER", User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers("/api/events/**").hasRole(User.Role.ADMIN.name())
                        // STAFF reads are additionally limited by InventoryService to active stock in its own Department; writes require ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/inventory/**")
                        .hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers("/api/inventory/**").hasRole(User.Role.ADMIN.name())
                        // Only the defined alert read/read-mark/resolve operations are allowed; services check ownership, and other alert routes are denied.
                        .requestMatchers(HttpMethod.GET, "/api/alerts/**").hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/alerts/*/read").hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/alerts/*").hasAnyRole(User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers("/api/alerts/**").denyAll()
                        // The service returns only the caller's permitted dashboard section; the four broader aggregate endpoints require ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/analytics/dashboard")
                        .hasAnyRole("USER", User.Role.STAFF.name(), User.Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/analytics/rooms", "/api/analytics/tasks",
                                "/api/analytics/departments", "/api/analytics/inventory-events").hasRole(User.Role.ADMIN.name())
                        .requestMatchers("/api/analytics/**").denyAll()
                        // Unmatched routes still require authentication; this fallback does not grant domain permissions.
                        .anyRequest().authenticated()
                )
                // Authenticate Bearer identities before route authorization examines their role authorities.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Allows only configured browser origins, supported methods, and Authorization/Content-Type headers.
     * Credentialed requests permit the refresh cookie without allowing arbitrary origins.
     */
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
