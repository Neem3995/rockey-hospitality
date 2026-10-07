package com.rockey.hospitality.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockey.hospitality.controller.*;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.security.*;
import com.rockey.hospitality.security.SecurityHandlers.RestAccessDeniedHandler;
import com.rockey.hospitality.security.SecurityHandlers.RestAuthenticationEntryPoint;
import com.rockey.hospitality.security.SecurityHandlers.SecurityErrorWriter;
import com.rockey.hospitality.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendContractTest.DocumentationApplication.class,
        properties = "springdoc.paths-to-match=/api/**")
@AutoConfigureMockMvc
class BackendContractTest {
    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EnableConfigurationProperties(SecurityProperties.class)
    @Import({AuthController.class, DepartmentController.class, EmployeeController.class,
            RoomController.class, TaskController.class, EventController.class, InventoryController.class,
            AlertController.class, AnalyticsController.class, ApplicationConfiguration.class,
            SecurityConfiguration.class, ApplicationConfiguration.class, JwtAuthenticationFilter.class,
            JwtService.class, RockeyUserDetailsService.class, RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class, SecurityErrorWriter.class})
    static class DocumentationApplication { }

    @MockitoBean AuthService auth;
    @MockitoBean DepartmentService departments;
    @MockitoBean EmployeeService employees;
    @MockitoBean RoomService rooms;
    @MockitoBean TaskService tasks;
    @MockitoBean EventService events;
    @MockitoBean RegistrationService registrations;
    @MockitoBean InventoryService inventory;
    @MockitoBean AlertService alerts;
    @MockitoBean AnalyticsService analytics;
    @MockitoBean UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void secret(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("rockey.security.jwt-secret", () -> Base64.getEncoder().encodeToString(key));
    }

    private String bearer(User.Role role) {
        User user = new User("Documentation Test", "documentation@example.test", "test-only-hash");
        ReflectionTestUtils.setField(user, "id", 999L);
        ReflectionTestUtils.setField(user, "role", role);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        return "Bearer " + jwt.issueAccessToken(user).getValue();
    }

    @Test
    void generatedContractMatchesAll51OperationsAndApprovedFilters() throws Exception {
        String body = mvc.perform(get("/v3/api-docs").header("Authorization", bearer(User.Role.ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode document = mapper.readTree(body);
        Set<String> expected = new TreeSet<>();
        java.util.regex.Pattern contractRow = java.util.regex.Pattern.compile("^\\|\\s*\\d+\\s*\\|.*");
        for (String line : Files.readAllLines(Path.of("..", "14_API_CONTRACT.md"))) {
            if (!contractRow.matcher(line).matches()) continue;
            String[] cells = line.split("\\|");
            if (cells.length < 7 || Integer.parseInt(cells[1].trim()) > 51) continue;
            expected.add(cells[2].replace("`", "").trim());
        }
        Set<String> actual = new TreeSet<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(operation -> {
            if (!Set.of("get", "post", "put", "patch", "delete").contains(operation.getKey())) return;
            actual.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey());
            assertFalse(operation.getValue().path("summary").asText().isBlank());
            assertFalse(operation.getValue().path("description").asText().isBlank());
        }));
        assertEquals(51, expected.size());
        assertEquals(expected, actual);
        assertFalse(document.at("/paths/~1api~1auth~1logout/post/responses/204").has("content"));
        assertEquals(0, document.at("/paths/~1api~1auth~1login/post/security").size());
        assertEquals("bearer", document.at("/components/securitySchemes/BearerAuth/scheme").asText());
        assertEquals("rockey_refresh", document.at("/components/securitySchemes/RefreshCookie/name").asText());
        assertTrue(document.at("/paths/~1api~1auth~1refresh/post/security/0").has("RefreshCookie"));
        assertEquals("/", document.at("/servers/0/url").asText());
        assertEquals(Set.of(), parameters(document, "/api/analytics/dashboard"));
        assertEquals(Set.of("departmentId"), parameters(document, "/api/analytics/inventory-events"));
        assertEquals(Set.of("departmentId", "page", "size"), parameters(document, "/api/analytics/departments"));
        assertFalse(document.path("components").path("schemas").has("RockeyUserPrincipal"));
        assertFalse(document.path("components").path("schemas").has("User"));
        Path artifact = Path.of("target", "openapi", "rockey-openapi.json");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document));
    }

    private Set<String> parameters(JsonNode document, String path) {
        Set<String> names = new TreeSet<>();
        document.path("paths").path(path).path("get").path("parameters")
                .forEach(parameter -> names.add(parameter.path("name").asText()));
        return names;
    }

    @ParameterizedTest
    @EnumSource(value = User.Role.class, names = {"USER", "STAFF"})
    void documentationIsAdminOnly(User.Role role) throws Exception {
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer(role))).andExpect(status().isForbidden());
    }

    @Test
    void documentationRequiresAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void approvedCorsPreflightAndUnapprovedOrigin() throws Exception {
        mvc.perform(options("/api/analytics/dashboard").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/analytics/dashboard").header("Origin", "https://unapproved.example.test")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://unapproved.example.test", "http://localhost:5174", "null"})
    void browserSimpleRefreshRequestCannotBypassOriginControls(String origin) throws Exception {
        mvc.perform(post("/api/auth/refresh").header("Origin", origin)
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .cookie(new jakarta.servlet.http.Cookie("rockey_refresh", "test-only-refresh")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void securityHeadersAndHttpsOnlyHsts() throws Exception {
        String token = bearer(User.Role.ADMIN);
        mvc.perform(get("/api/analytics/dashboard").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Cache-Control")).andExpect(header().doesNotExist("Strict-Transport-Security"));
        mvc.perform(get("/api/analytics/dashboard").secure(true).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    void approvedSignedIntegerParsingIsUnchanged() throws Exception {
        mvc.perform(get("/api/analytics/rooms").param("floor", "+1").header("Authorization", bearer(User.Role.ADMIN)))
                .andExpect(status().isOk());
        verify(analytics).rooms(1, User.Role.ADMIN);
    }
}
