package com.rockey.hospitality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.repository.AnalyticsRepository;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.AnalyticsService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {
    @Mock private AnalyticsRepository analytics;
    @Mock private DepartmentRepository departments;
    @Mock private EmployeeRepository employees;
    private MockMvc mvc;
    private ObjectMapper mapper;

    @BeforeEach void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        AnalyticsService service = new AnalyticsService(analytics, employees, departments,
                Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC));
        mvc = standaloneSetup(new AnalyticsController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        authenticate(User.Role.ADMIN);
    }
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void userJsonContainsExactlyRoleAsOfAndOwnRegistrationSection() throws Exception {
        authenticate(User.Role.USER); when(analytics.countRegistrations(31L)).thenReturn(3L);
        String json = mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.user.registrationCount").value(3))
                .andExpect(jsonPath("$.staff").doesNotExist()).andExpect(jsonPath("$.admin").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("role", "asOf", "user"), keys(json));
        assertEquals(Set.of("registrationCount"), keys(mapper.readTree(json).get("user").toString()));
        verifyNoInteractions(employees, departments);
    }

    @Test void staffJsonContainsOnlyApprovedScopedMetrics() throws Exception {
        authenticate(User.Role.STAFF);
        Department department = new Department("Housekeeping", "Operations"); ReflectionTestUtils.setField(department, "id", 3L);
        Employee employee = new Employee("Worker", "worker@example.test", department, "Staff", null);
        ReflectionTestUtils.setField(employee, "id", 12L); when(employees.findByUserId(31L)).thenReturn(Optional.of(employee));
        String json = mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.employeeId").value(12)).andExpect(jsonPath("$.staff.departmentId").value(3))
                .andExpect(jsonPath("$.user").doesNotExist()).andExpect(jsonPath("$.admin").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("role", "asOf", "staff"), keys(json));
        assertEquals(Set.of("employeeId", "departmentId", "nonTerminalAssignedTaskCount", "overdueAssignedTaskCount",
                "unreadAlertCount", "unresolvedAlertCount", "activeInventoryItemCount", "lowStockItemCount"),
                keys(mapper.readTree(json).get("staff").toString()));
    }

    @Test void adminDashboardHasExactlyApprovedMetricsAndNoSensitiveData() throws Exception {
        String json = mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.staff").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertEquals(Set.of("activeRoomCount", "readyRoomCount", "nonTerminalTaskCount", "overdueTaskCount", "completedTaskCount",
                "activeDepartmentCount", "unresolvedAlertCount", "activeInventoryItemCount", "lowStockItemCount",
                "nonTerminalEventCount", "registrationCount"), keys(mapper.readTree(json).get("admin").toString()));
        assertFalse(json.contains("password")); assertFalse(json.contains("Token")); assertFalse(json.contains("email"));
    }

    @Test void roomsHaveCanonicalZeroFilledStatusMapAndNullFloorByDefault() throws Exception {
        mvc.perform(get("/api/analytics/rooms")).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRoomCount").value(0)).andExpect(jsonPath("$.roomCountsByStatus", aMapWithSize(7)))
                .andExpect(jsonPath("$.roomCountsByStatus.READY").value(0));
        verify(analytics).roomCounts(null);
    }

    @Test void roomFilterPassesExactFloor() throws Exception {
        mvc.perform(get("/api/analytics/rooms").param("floor", "99")).andExpect(status().isOk()).andExpect(jsonPath("$.floor").value(99));
        verify(analytics).roomCounts(99);
    }

    @Test void taskFilterCanBeUnknownAndReturns200EmptyCounts() throws Exception {
        mvc.perform(get("/api/analytics/tasks").param("departmentId", "999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(999)).andExpect(jsonPath("$.totalTaskCount").value(0))
                .andExpect(jsonPath("$.taskCountsByStatus", aMapWithSize(5)));
        verify(analytics).taskCounts(999L, null); verifyNoInteractions(departments);
    }

    @Test void emptyDepartmentPageUsesCanonicalDefaults() throws Exception {
        mvc.perform(get("/api/analytics/departments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.departments.page").value(0)).andExpect(jsonPath("$.departments.size").value(20))
                .andExpect(jsonPath("$.departments.totalElements").value(0)).andExpect(jsonPath("$.departments.last").value(true));
    }

    @Test void validMaximumSizeAndFarOutPageAreEmptyNot400() throws Exception {
        mvc.perform(get("/api/analytics/departments").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departments.size").value(100));
    }

    @Test void explicitMissingDepartmentUsesCanonical404() throws Exception {
        mvc.perform(get("/api/analytics/departments").param("departmentId", "999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/analytics/departments"));
    }

    @Test void combinedFilterScopesOnlyInventoryAndKeepsGlobalEvents() throws Exception {
        when(analytics.countRegistrations(null)).thenReturn(8L);
        mvc.perform(get("/api/analytics/inventory-events").param("departmentId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inventory.departmentId").value(999))
                .andExpect(jsonPath("$.inventory.activeInventoryItemCount").value(0))
                .andExpect(jsonPath("$.events.registrationCount").value(8))
                .andExpect(jsonPath("$.events.eventCountsByStatus", aMapWithSize(6)));
        verify(analytics).activeInventoryItemCount(999L); verify(analytics).eventCounts();
    }

    @Test void ineligibleStaffReceivesSafe403() throws Exception {
        authenticate(User.Role.STAFF);
        mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(analytics);
    }

    @ParameterizedTest @CsvSource({"rooms,floor,0", "rooms,floor,100", "rooms,floor,x", "rooms,floor,2147483648",
            "tasks,departmentId,0", "tasks,departmentId,-1", "tasks,departmentId,x", "tasks,departmentId,9223372036854775808",
            "departments,page,-1", "departments,page,x", "departments,size,0", "departments,size,101",
            "departments,departmentId,0", "inventory-events,departmentId,-1"})
    void invalidKnownParametersUseSafe400(String endpoint, String parameter, String value) throws Exception {
        mvc.perform(get("/api/analytics/" + endpoint).param(parameter, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(analytics);
    }

    @ParameterizedTest @ValueSource(strings = {"dashboard", "rooms", "tasks", "departments", "inventory-events"})
    void unsupportedParametersIncludingDateWindowsRejected(String endpoint) throws Exception {
        mvc.perform(get("/api/analytics/" + endpoint).param("from", "2030-01-01").param("to", "2020-01-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(analytics);
    }

    @ParameterizedTest @CsvSource({"rooms,floor", "tasks,departmentId", "departments,page", "departments,size", "inventory-events,departmentId"})
    void repeatedAndEmptyParametersRejected(String endpoint, String parameter) throws Exception {
        mvc.perform(get("/api/analytics/" + endpoint).param(parameter, "1", "2")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analytics/" + endpoint).param(parameter, "")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analytics/" + endpoint).param(parameter, " ")).andExpect(status().isBadRequest());
        verifyNoInteractions(analytics);
    }

    private void authenticate(User.Role role) {
        User user = new User("Viewer", "viewer@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", 31L); ReflectionTestUtils.setField(user, "role", role);
        RockeyUserPrincipal principal = new RockeyUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    private Set<String> keys(String json) throws Exception {
        Set<String> keys = new HashSet<>(); mapper.readTree(json).fieldNames().forEachRemaining(keys::add); return keys;
    }
}
