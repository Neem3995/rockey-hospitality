package com.rockey.hospitality.configuration;

import com.rockey.hospitality.controller.AnalyticsController;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.security.*;
import com.rockey.hospitality.security.SecurityHandlers.RestAccessDeniedHandler;
import com.rockey.hospitality.security.SecurityHandlers.RestAuthenticationEntryPoint;
import com.rockey.hospitality.security.SecurityHandlers.SecurityErrorWriter;
import com.rockey.hospitality.service.AnalyticsService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@Import({ApplicationConfiguration.class, SecurityConfiguration.class, JwtAuthenticationFilter.class,
        JwtService.class, RockeyUserDetailsService.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityErrorWriter.class})
@EnableConfigurationProperties(SecurityProperties.class)
class AnalyticsSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private AnalyticsService service;
    @MockitoBean private UserRepository users;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        registry.add("rockey.security.jwt-secret", () -> Base64.getEncoder().encodeToString(key));
    }

    @ParameterizedTest @ValueSource(strings = {"dashboard", "rooms", "tasks", "departments", "inventory-events"})
    void unauthenticatedRequestsUseExisting401(String endpoint) throws Exception {
        mvc.perform(get("/api/analytics/" + endpoint)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "STAFF", "ADMIN"})
    void allCanonicalRolesReachDashboardWithExistingJwtIdentity(String name) throws Exception {
        User.Role role = User.Role.valueOf(name);
        mvc.perform(get("/api/analytics/dashboard").header("Authorization", "Bearer " + token(role))).andExpect(status().isOk());
        verify(service).dashboard(31L, role);
    }

    @ParameterizedTest @CsvSource({"USER,rooms", "USER,tasks", "USER,departments", "USER,inventory-events",
            "STAFF,rooms", "STAFF,tasks", "STAFF,departments", "STAFF,inventory-events"})
    void operationalAnalyticsRejectUserAndStaff(String name, String endpoint) throws Exception {
        mvc.perform(get("/api/analytics/" + endpoint).header("Authorization", "Bearer " + token(User.Role.valueOf(name))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"rooms", "tasks", "departments", "inventory-events"})
    void adminReachesEachApprovedEndpoint(String endpoint) throws Exception {
        mvc.perform(get("/api/analytics/" + endpoint).header("Authorization", "Bearer " + token(User.Role.ADMIN))).andExpect(status().isOk());
        switch (endpoint) {
            case "rooms" -> verify(service).rooms(null, User.Role.ADMIN);
            case "tasks" -> verify(service).tasks(null, User.Role.ADMIN);
            case "departments" -> verify(service).departments(null, 0, 20, User.Role.ADMIN);
            default -> verify(service).operations(null, User.Role.ADMIN);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"dashboard", "rooms", "tasks", "departments", "inventory-events"})
    void analyticsCannotBeMutatedEvenByAdmin(String endpoint) throws Exception {
        String authorization = "Bearer " + token(User.Role.ADMIN);
        mvc.perform(post("/api/analytics/" + endpoint).header("Authorization", authorization)).andExpect(status().isForbidden());
        mvc.perform(put("/api/analytics/" + endpoint).header("Authorization", authorization)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/analytics/" + endpoint).header("Authorization", authorization)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private String token(User.Role role) {
        User user = new User("Viewer", "viewer@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", 31L); ReflectionTestUtils.setField(user, "role", role);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        return jwt.issueAccessToken(user).getValue();
    }
}
