package com.rockey.hospitality.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiConfigurationTest {
    private final OpenApiConfiguration configuration = new OpenApiConfiguration();

    @Test
    void absentPathsAndAbsentPostOperationsRemainSafe() {
        var customizer = configuration.publicAuthenticationDocumentation();
        customizer.customise(new OpenAPI());
        OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/api/auth/login", new PathItem()));
        customizer.customise(api);
        assertNull(api.getPaths().get("/api/auth/login").getPost());
    }

    @Test
    void onlyPublicAuthenticationOverridesDefaultBearerRequirement() {
        OpenAPI api = configuration.rockeyOpenApi().paths(new Paths()
                .addPathItem("/api/auth/register", new PathItem().post(new Operation()))
                .addPathItem("/api/auth/login", new PathItem().post(new Operation()))
                .addPathItem("/api/auth/refresh", new PathItem().post(new Operation()))
                .addPathItem("/api/tasks", new PathItem().get(new Operation())));
        configuration.publicAuthenticationDocumentation().customise(api);
        assertTrue(api.getPaths().get("/api/auth/register").getPost().getSecurity().isEmpty());
        assertTrue(api.getPaths().get("/api/auth/login").getPost().getSecurity().isEmpty());
        assertTrue(api.getPaths().get("/api/auth/refresh").getPost().getSecurity().get(0).containsKey("RefreshCookie"));
        assertNull(api.getPaths().get("/api/tasks").getGet().getSecurity());
        assertTrue(api.getSecurity().get(0).containsKey("BearerAuth"));
    }

    @Test
    void wildcardNormalizationPreservesSchemaAndOtherMediaTypes() {
        MediaType wildcard = new MediaType();
        MediaType text = new MediaType();
        Content content = new Content().addMediaType("*/*", wildcard).addMediaType("text/plain", text);
        ApiResponses responses = new ApiResponses()
                .addApiResponse("200", new ApiResponse().content(content))
                .addApiResponse("204", new ApiResponse())
                .addApiResponse("400", new ApiResponse().content(new Content()));
        OpenAPI api = new OpenAPI().paths(new Paths()
                .addPathItem("/api/tasks", new PathItem().get(new Operation().responses(responses)))
                .addPathItem("/api/rooms", new PathItem().get(new Operation())));
        configuration.publicAuthenticationDocumentation().customise(api);
        assertFalse(content.containsKey("*/*"));
        assertSame(wildcard, content.get("application/json"));
        assertSame(text, content.get("text/plain"));
        assertEquals(3, responses.size());
    }
}
