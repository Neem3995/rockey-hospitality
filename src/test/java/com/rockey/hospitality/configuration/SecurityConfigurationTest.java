package com.rockey.hospitality.configuration;

import com.rockey.hospitality.controller.AlertController;
import com.rockey.hospitality.controller.AuthController;
import com.rockey.hospitality.controller.DepartmentController;
import com.rockey.hospitality.controller.EmployeeController;
import com.rockey.hospitality.controller.EventController;
import com.rockey.hospitality.controller.InventoryController;
import com.rockey.hospitality.controller.RoomController;
import com.rockey.hospitality.controller.TaskController;
import com.rockey.hospitality.dto.AuthDtos.AuthResponse;
import com.rockey.hospitality.dto.AuthDtos.CurrentUserResponse;
import com.rockey.hospitality.dto.AuthDtos.DepartmentSummary;
import com.rockey.hospitality.dto.CommonDtos.PageCriteria;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.EmployeeDtos.CreateEmployeeRequest;
import com.rockey.hospitality.dto.EmployeeDtos.EmployeeResponse;
import com.rockey.hospitality.dto.InventoryDtos.CreateInventoryItemRequest;
import com.rockey.hospitality.dto.InventoryDtos.UpdateInventoryItemRequest;
import com.rockey.hospitality.dto.RoomDtos.CreateRoomRequest;
import com.rockey.hospitality.dto.RoomDtos.RoomResponse;
import com.rockey.hospitality.dto.RoomDtos.RoomSearchCriteria;
import com.rockey.hospitality.dto.TaskDtos.CreateTaskRequest;
import com.rockey.hospitality.dto.TaskDtos.TaskResponse;
import com.rockey.hospitality.dto.TaskDtos.TaskSearchCriteria;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.security.JwtAuthenticationFilter;
import com.rockey.hospitality.security.JwtService;
import com.rockey.hospitality.security.RockeyUserDetailsService;
import com.rockey.hospitality.security.SecurityHandlers.RestAccessDeniedHandler;
import com.rockey.hospitality.security.SecurityHandlers.RestAuthenticationEntryPoint;
import com.rockey.hospitality.security.SecurityHandlers.SecurityErrorWriter;
import com.rockey.hospitality.service.AlertService;
import com.rockey.hospitality.service.AuthService;
import com.rockey.hospitality.service.DepartmentService;
import com.rockey.hospitality.service.EmployeeService;
import com.rockey.hospitality.service.EventService;
import com.rockey.hospitality.service.InventoryService;
import com.rockey.hospitality.service.RegistrationService;
import com.rockey.hospitality.service.RoomService;
import com.rockey.hospitality.service.TaskService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        DepartmentController.class,
        AuthController.class,
        EmployeeController.class,
        RoomController.class,
        TaskController.class,
        EventController.class,
        InventoryController.class,
        AlertController.class
})
@Import({
        ApplicationConfiguration.class,
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        RockeyUserDetailsService.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityErrorWriter.class
})
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private AlertService alertService;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        byte[] signingKey = new byte[32];
        new SecureRandom().nextBytes(signingKey);
        registry.add(
                "rockey.security.jwt-secret",
                () -> Base64.getEncoder().encodeToString(signingKey)
        );
        registry.add("rockey.security.access-token-minutes", () -> "15");
        registry.add("rockey.security.refresh-token-days", () -> "7");
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        when(authService.login(
                "user@example.test",
                "valid-password",
                "127.0.0.1"
        )).thenReturn(authSession(1L, "user@example.test", User.Role.USER));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.test",
                                  "password": "valid-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void authenticatedAccountCanReadOwnProfile() throws Exception {
        String token = tokenFor(1L, "user@example.test", User.Role.USER);
        CurrentUserResponse response = currentUser(1L, "user@example.test", User.Role.USER);
        when(authService.getCurrentUser(1L)).thenReturn(response);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void authenticatedLogoutRevokesSessionAndExpiresRefreshCookie() throws Exception {
        String token = tokenFor(1L, "user@example.test", User.Role.USER);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("rockey_refresh=")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("Max-Age=0")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("HttpOnly")
                ));

        verify(authService).logout(1L);
    }

    @Test
    void unauthenticatedLogoutIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/logout"));
    }

    @Test
    void unauthenticatedDepartmentRequestReturnsCanonical401() throws Exception {
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/departments"));
    }

    @Test
    void malformedBearerTokenReturnsCanonical401() throws Exception {
        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void userRoleCannotReadOperationalDepartments() throws Exception {
        String token = tokenFor(1L, "user@example.test", User.Role.USER);

        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void staffRoleCanReadDepartments() throws Exception {
        when(departmentService.listDepartments(null)).thenReturn(List.of());
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);

        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void staffRoleCannotManageDepartments() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);

        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Security"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleCanDeactivateDepartment() throws Exception {
        String token = tokenFor(3L, "admin@example.test", User.Role.ADMIN);

        mockMvc.perform(delete("/api/departments/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(departmentService).deactivateDepartment(1L);
    }

    @Test
    void inactiveAccountCannotUsePreviouslyIssuedAccessToken() throws Exception {
        User user = user(4L, "inactive@example.test", User.Role.STAFF);
        String token = jwtService.issueAccessToken(user).getValue();
        ReflectionTestUtils.setField(user, "status", User.Status.INACTIVE);
        when(userRepository.findByEmailIgnoreCase("inactive@example.test"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changedServerRoleInvalidatesStaleRoleClaim() throws Exception {
        User user = user(5L, "changed@example.test", User.Role.USER);
        String token = jwtService.issueAccessToken(user).getValue();
        ReflectionTestUtils.setField(user, "role", User.Role.STAFF);
        when(userRepository.findByEmailIgnoreCase("changed@example.test"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedEmployeeListIsRejected() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void staffCannotListEmployees() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);

        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListEmployees() throws Exception {
        String token = tokenFor(3L, "admin@example.test", User.Role.ADMIN);
        when(employeeService.listEmployees(null, null, 0, 20, "name,asc"))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void linkedStaffCanReadOwnEmployeeRecord() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);
        when(employeeService.getEmployee(12L, 2L, User.Role.STAFF))
                .thenReturn(employeeResponse(12L, 2L));

        mockMvc.perform(get("/api/employees/12")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12));

        verify(employeeService).getEmployee(12L, 2L, User.Role.STAFF);
    }

    @Test
    void staffCannotUpdateEmployee() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/employees/12")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Worker Name",
                                  "email": "worker@example.test",
                                  "departmentId": 3,
                                  "jobRole": "Room Attendant",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateEmployee() throws Exception {
        String token = tokenFor(3L, "admin@example.test", User.Role.ADMIN);
        when(employeeService.createEmployee(any(CreateEmployeeRequest.class)))
                .thenReturn(employeeResponse(12L, null));

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Worker Name",
                                  "email": "worker@example.test",
                                  "departmentId": 3,
                                  "jobRole": "Room Attendant",
                                  "createLogin": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12));
    }

    @Test
    void unauthenticatedRoomListIsRejected() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void userCannotReadRooms() throws Exception {
        String token = tokenFor(1L, "user@example.test", User.Role.USER);

        mockMvc.perform(get("/api/rooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanListRooms() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);
        when(roomService.listRooms(new RoomSearchCriteria(null, null, null, null), new PageCriteria(0, 20, "roomNumber,asc"), User.Role.STAFF)).thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/rooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void staffCannotCreateRoom() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoomCreateJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateRoom() throws Exception {
        String token = tokenFor(3L, "admin@example.test", User.Role.ADMIN);
        when(roomService.createRoom(any(CreateRoomRequest.class)))
                .thenReturn(roomResponse());

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoomCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber").value("218"));
    }

    @Test
    void staffCanRequestRoomStatusTransition() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);
        when(roomService.updateStatus(
                12L,
                Room.Status.CLEANING,
                2L,
                User.Role.STAFF
        )).thenReturn(roomResponse());

        mockMvc.perform(patch("/api/rooms/12/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CLEANING"}
                                """))
                .andExpect(status().isOk());

        verify(roomService).updateStatus(
                12L,
                Room.Status.CLEANING,
                2L,
                User.Role.STAFF
        );
    }

    @Test
    void userCannotRequestRoomStatusTransition() throws Exception {
        String token = tokenFor(1L, "user@example.test", User.Role.USER);

        mockMvc.perform(patch("/api/rooms/12/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CLEANING"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedTaskListIsRejected() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void userCannotReadTasks() throws Exception {
        String token = tokenFor(1L, "user@example.test", User.Role.USER);

        mockMvc.perform(get("/api/tasks/41")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotSearchAllTasks() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSearchAllTasks() throws Exception {
        String token = tokenFor(3L, "admin@example.test", User.Role.ADMIN);
        when(taskService.listTasks(new TaskSearchCriteria(null, null, null, null, null, null, null), new PageCriteria(0, 20, "createdAt,desc"))).thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void staffCanRequestAssignedTaskDetail() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);
        when(taskService.getTask(41L, 2L, User.Role.STAFF)).thenReturn(taskResponse());

        mockMvc.perform(get("/api/tasks/41")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41));

        verify(taskService).getTask(41L, 2L, User.Role.STAFF);
    }

    @Test
    void staffCanRequestAssignedTaskCompletion() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);
        when(taskService.completeTask(41L, 2L, User.Role.STAFF)).thenReturn(taskResponse());

        mockMvc.perform(patch("/api/tasks/41/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void staffCanRequestOwnAssignedTaskList() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);
        when(taskService.listAssignedTasks(12L, null, null, null, new PageCriteria(0, 20, "createdAt,desc"), 2L, User.Role.STAFF)).thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/tasks/assigned/12")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void staffCannotCreateOrAssignTasks() throws Exception {
        String token = tokenFor(2L, "staff@example.test", User.Role.STAFF);

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTaskCreateJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/tasks/41/assigned-employee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":12}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateTask() throws Exception {
        String token = tokenFor(3L, "admin@example.test", User.Role.ADMIN);
        when(taskService.createTask(any(CreateTaskRequest.class))).thenReturn(taskResponse());

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTaskCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(41));
    }

    @Test
    void allAuthenticatedRolesCanBrowseEvents() throws Exception {
        when(eventService.listEvents(null, null, null, 0, 20, "eventDateTime,asc"))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true));

        for (User.Role role : User.Role.values()) {
            long id = role.ordinal() + 10L;
            String email = role.name().toLowerCase() + "-events@example.test";
            String token = tokenFor(id, email, role);
            mockMvc.perform(get("/api/events")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void onlyUserMayUseSelfRegistrationEndpoints() throws Exception {
        String userToken = tokenFor(31L, "attendee@example.test", User.Role.USER);
        mockMvc.perform(post("/api/events/7/registrations")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/events/registrations/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/events/7/registrations/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        String staffToken = tokenFor(21L, "event-staff@example.test", User.Role.STAFF);
        mockMvc.perform(post("/api/events/7/registrations")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/events/registrations/me")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        String adminToken = tokenFor(3L, "event-admin@example.test", User.Role.ADMIN);
        mockMvc.perform(post("/api/events/7/registrations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminMayManageEvents() throws Exception {
        String userToken = tokenFor(31L, "event-user@example.test", User.Role.USER);
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventCreateJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/events/7")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String adminToken = tokenFor(3L, "events-admin@example.test", User.Role.ADMIN);
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventCreateJson()))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/events/7")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void inventoryRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/inventory")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inventory/88")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/inventory")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/inventory/88")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/inventory/88")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReadInventory() throws Exception {
        String token = tokenFor(31L, "inventory-user@example.test", User.Role.USER);
        mockMvc.perform(get("/api/inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/inventory/88").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffAndAdminMayReadInventory() throws Exception {
        for (User.Role role : List.of(User.Role.STAFF, User.Role.ADMIN)) {
            Long id = role == User.Role.STAFF ? 21L : 3L;
            String token = tokenFor(id, role.name().toLowerCase() + "-inventory@example.test", role);
            mockMvc.perform(get("/api/inventory").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/inventory/88").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            verify(inventoryService).getInventoryItem(88L, id, role);
        }
    }

    @Test
    void staffAndUserCannotManageInventory() throws Exception {
        for (User.Role role : List.of(User.Role.STAFF, User.Role.USER)) {
            String token = tokenFor((long) role.ordinal() + 10L,
                    role.name().toLowerCase() + "-inventory-write@example.test", role);
            mockMvc.perform(post("/api/inventory").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(validInventoryCreateJson()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/inventory/88").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(validInventoryUpdateJson()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/inventory/88").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminMayCreateRestockAndSoftDeactivateInventory() throws Exception {
        String token = tokenFor(3L, "inventory-admin@example.test", User.Role.ADMIN);
        mockMvc.perform(post("/api/inventory").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(validInventoryCreateJson()))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/inventory/88").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(validInventoryUpdateJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/inventory/88").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        verify(inventoryService).createInventoryItem(any(CreateInventoryItemRequest.class));
        verify(inventoryService).updateInventoryItem(any(Long.class), any(UpdateInventoryItemRequest.class));
        verify(inventoryService).deactivateInventoryItem(88L);
    }

    private String validInventoryCreateJson() {
        return """
                {"name":"Bath Towels","sku":"HK-TOWEL-BATH","quantity":18,
                 "reorderThreshold":20,"departmentId":3}
                """;
    }

    private String validInventoryUpdateJson() {
        return """
                {"name":"Bath Towels","quantity":50,"reorderThreshold":20,
                 "departmentId":3,"active":true}
                """;
    }

    @Test
    void alertEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/alerts/501")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/alerts/501/read")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/alerts/501")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAnyAlertOperation() throws Exception {
        String token = tokenFor(31L, "alert-user@example.test", User.Role.USER);
        mockMvc.perform(get("/api/alerts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alerts/501").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/alerts/501/read").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/alerts/501").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void staffAndAdminReachCanonicalAlertOperationsWithPrincipalIdentity() throws Exception {
        for (User.Role role : List.of(User.Role.STAFF, User.Role.ADMIN)) {
            Long id = role == User.Role.STAFF ? 21L : 3L;
            String token = tokenFor(id, role.name().toLowerCase() + "-alerts@example.test", role);
            mockMvc.perform(get("/api/alerts").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            mockMvc.perform(get("/api/alerts/501").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            mockMvc.perform(put("/api/alerts/501/read").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            mockMvc.perform(delete("/api/alerts/501").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
            verify(alertService).getAlert(501L, id, role);
            verify(alertService).markRead(501L, id, role);
            verify(alertService).resolveAlert(501L, id, role);
        }
    }

    @Test
    void noManualAlertCreationOrGenericUpdateRouteIsGranted() throws Exception {
        String token = tokenFor(3L, "alert-admin@example.test", User.Role.ADMIN);
        mockMvc.perform(post("/api/alerts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/alerts/501").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    private String tokenFor(Long id, String email, User.Role role) {
        User user = user(id, email, role);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        return jwtService.issueAccessToken(user).getValue();
    }

    private User user(Long id, String email, User.Role role) {
        User user = new User("Test User", email, "bcrypt-hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private AuthService.AuthSession authSession(Long id, String email, User.Role role) {
        return new AuthService.AuthSession(
                new AuthResponse(
                        "access-token",
                        "Bearer",
                        Instant.parse("2026-10-03T20:15:00Z"),
                        Instant.parse("2026-10-10T20:00:00Z"),
                        currentUser(id, email, role)
                ),
                "runtime-test-refresh-token"
        );
    }

    private CurrentUserResponse currentUser(Long id, String email, User.Role role) {
        return new CurrentUserResponse(
                id,
                "Test User",
                email,
                role,
                null,
                User.Status.ACTIVE,
                null
        );
    }

    private EmployeeResponse employeeResponse(Long employeeId, Long userId) {
        return new EmployeeResponse(
                employeeId,
                userId,
                "Worker Name",
                "worker@example.test",
                new DepartmentSummary(3L, "Housekeeping"),
                "Room Attendant",
                Employee.Status.ACTIVE,
                LocalDateTime.of(2026, 10, 3, 9, 0),
                LocalDateTime.of(2026, 10, 3, 9, 0)
        );
    }

    private String validRoomCreateJson() {
        return """
                {
                  "roomNumber": "218",
                  "roomType": "STANDARD",
                  "floor": 2,
                  "initialStatus": "DIRTY",
                  "nextArrivalAt": "2030-10-03T15:00:00"
                }
                """;
    }

    private RoomResponse roomResponse() {
        return new RoomResponse(
                12L,
                "218",
                "STANDARD",
                Room.Status.DIRTY,
                2,
                LocalDateTime.of(2030, 10, 3, 15, 0),
                true,
                LocalDateTime.of(2026, 10, 3, 9, 0),
                LocalDateTime.of(2026, 10, 3, 10, 0)
        );
    }

    private String validTaskCreateJson() {
        return """
                {
                  "title": "Inspect room",
                  "departmentId": 3,
                  "priority": "HIGH",
                  "dueAt": "2030-10-04T15:00:00"
                }
                """;
    }

    private String validEventCreateJson() {
        return """
                {
                  "title":"Leadership Conference",
                  "eventDateTime":"2030-10-10T09:00:00",
                  "location":"Ballroom A",
                  "capacity":120,
                  "initialStatus":"OPEN"
                }
                """;
    }

    private TaskResponse taskResponse() {
        return new TaskResponse(
                41L,
                "Inspect room",
                "Check readiness.",
                Task.Status.ASSIGNED,
                Task.Priority.HIGH,
                new DepartmentSummary(3L, "Housekeeping"),
                null,
                null,
                LocalDateTime.of(2026, 10, 3, 9, 0),
                LocalDateTime.of(2030, 10, 4, 15, 0),
                null,
                LocalDateTime.of(2026, 10, 3, 10, 0)
        );
    }
}
