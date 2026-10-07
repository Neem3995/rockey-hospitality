package com.rockey.hospitality.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Describes the existing business API for OpenAPI clients.
 * Documentation metadata does not replace the security filter's access checks.
 */
// Marks this class as Spring configuration supplying application beans.
@Configuration
public class OpenApiConfiguration {
    /**
     * Builds the API title, security schemes, and same-origin server description.
     * Bearer authentication is the documentation default, not an access-control implementation.
     */
    // Registers this method's returned object as a shared Spring-managed dependency.
    @Bean
    public OpenAPI rockeyOpenApi() {
        return new OpenAPI().info(new Info().title("Rockey Hospitality API").version("1.0")
                .description("Frozen 51-operation business contract. Documentation requires ADMIN. "
                        + "USER: own registrations; STAFF: authorized identity/Department scope; ADMIN: operational oversight. "
                        + "See the canonical API contract for lifecycle, validation and scope rules."))
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
    // Registers this method's returned object as a shared Spring-managed dependency.
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
}
