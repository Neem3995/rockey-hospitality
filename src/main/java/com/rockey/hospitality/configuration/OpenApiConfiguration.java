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

@Configuration
public class OpenApiConfiguration {
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

    @Bean
    public OpenApiCustomizer publicAuthenticationDocumentation() {
        return api -> {
            normalizeResponseContent(api);
            configurePublicAuthentication(api);
        };
    }

    private void normalizeResponseContent(OpenAPI api) {
        if (api.getPaths() == null) return;
        api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            if (operation.getResponses() != null) operation.getResponses().values().forEach(this::normalizeResponse);
        }));
    }

    private void normalizeResponse(io.swagger.v3.oas.models.responses.ApiResponse response) {
        if (response.getContent() != null && response.getContent().containsKey("*/*")) {
            response.getContent().addMediaType("application/json", response.getContent().remove("*/*"));
        }
    }

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
