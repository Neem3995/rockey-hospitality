package com.rockey.hospitality.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.time.Clock;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * STUDY NOTE: Configuration is code that sets up Spring and application features.
 * Here, @Configuration marks this setup class; @Bean creates an object managed by Spring's application context.
 * Shared Clock and BCrypt beans give services time and password-hashing tools.
 * OpenAPI beans describe the existing controllers for API documentation; SecurityConfiguration still
 * controls access.
 */
@Configuration
public class ApplicationConfiguration {

    // OpenAPI is a machine-readable endpoint contract; Swagger-compatible tools can display it interactively.
    // Rockey exposes ADMIN-only JSON/YAML documentation, not a public Swagger UI.
    // A Spring bean is an object the application context creates and injects into its collaborators.

    /**
     * Provides the shared UTC Clock for injectable time calculations.
     * Services needing server-local LocalDateTime explicitly choose that zone.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Provides BCrypt encoding and matching for passwords.
     * A stored hash is compared through PasswordEncoder rather than decrypted.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // OpenAPI metadata documents the existing routes; it does not grant access.
/**
     * Builds the API title, security schemes, and same-origin server description.
     * Bearer authentication is the documentation default, not an access-control implementation.
     */
    @Bean
    public OpenAPI rockeyOpenApi() {
        return new OpenAPI().info(new Info().title("Rockey Hospitality API").version("1.0")
                .description("Housekeeping-only API. USER: housekeeper; MANAGER: supervisor; ADMIN: account oversight."))
                .components(new Components().addSecuritySchemes("BearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("RefreshCookie", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("rockey_refresh")))
                .security(List.of(new SecurityRequirement().addList("BearerAuth")))
                .servers(List.of(new Server().url("/").description("Current application origin")));
    }

    /**
     * Registers an OpenAPI customizer that normalizes response media types and describes public authentication operations accurately.
     */
    @Bean
    public OpenApiCustomizer publicAuthenticationDocumentation() {
        return api -> {
            normalizeResponseContent(api);
            configurePublicAuthentication(api);
        };
    }

    /**
     * Visits documented operation responses and delegates media-type normalization, safely doing nothing when paths are absent.
     */
    private void normalizeResponseContent(OpenAPI api) {
        if (api.getPaths() == null) return;
        api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            if (operation.getResponses() != null) operation.getResponses().values().forEach(this::normalizeResponse);
        }));
    }

    /**
     * Replaces a generated wildcard response media type with application/json in documentation only.
     */
    private void normalizeResponse(io.swagger.v3.oas.models.responses.ApiResponse response) {
        if (response.getContent() != null && response.getContent().containsKey("*/*")) {
            response.getContent().addMediaType("application/json", response.getContent().remove("*/*"));
        }
    }

    /**
     * Removes the default Bearer documentation requirement from register and login.
     * Refresh documents the refresh cookie instead of an access JWT.
     */
    private void configurePublicAuthentication(OpenAPI api) {
        if (api.getPaths() == null) return;
        for (String path : List.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh")) {
            var item = api.getPaths().get(path);
            if (item == null || item.getPost() == null) continue;
            // Refresh still requires its refresh cookie/token, not a Bearer access token.
            item.getPost().setSecurity(path.endsWith("/refresh")
                    ? List.of(new SecurityRequirement().addList("RefreshCookie")) : List.of());
        }
    }
    /** The small account loader bean bridges MySQL users and JWT authentication. */
    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .map(RockeyUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
    }
}
