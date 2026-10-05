package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.task.TaskSearchCriteria;
import com.rockey.hospitality.dto.common.PageCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.task.AssignTaskRequest;
import com.rockey.hospitality.dto.task.CreateTaskRequest;
import com.rockey.hospitality.dto.task.TaskEventSummary;
import com.rockey.hospitality.dto.task.TaskEmployeeSummary;
import com.rockey.hospitality.dto.task.TaskResponse;
import com.rockey.hospitality.dto.task.TaskRoomSummary;
import com.rockey.hospitality.dto.task.UpdateTaskRequest;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.EventStatus;
import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = standaloneSetup(new TaskController(taskService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.destroy();
    }

    @Test
    void listTasksPassesFiltersIncludingOverdue() throws Exception {
        when(taskService.listTasks(new TaskSearchCriteria(3L, TaskStatus.ASSIGNED, TaskPriority.HIGH, 12L, 218L, 7L, true), new PageCriteria(1, 5, "dueAt,asc"))).thenReturn(new PagedResponse<>(List.of(response()), 1, 5, 6, 2, true));

        mockMvc.perform(get("/api/tasks")
                        .param("departmentId", "3")
                        .param("status", "ASSIGNED")
                        .param("priority", "HIGH")
                        .param("assignedEmployeeId", "12")
                        .param("roomId", "218")
                        .param("eventId", "7")
                        .param("overdue", "true")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "dueAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(41))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void createTaskReturns201WithOptionalEventSummary() throws Exception {
        when(taskService.createTask(any(CreateTaskRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.department.id").value(3))
                .andExpect(jsonPath("$.event.id").value(7))
                .andExpect(jsonPath("$.eventId").doesNotExist());
    }

    @Test
    void invalidCreateReturnsCanonical400() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "x",
                                  "departmentId": 0,
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.departmentId").exists());
    }

    @Test
    void getTaskPassesAuthenticatedIdentity() throws Exception {
        when(taskService.getTask(41L, 21L, Role.STAFF)).thenReturn(response());

        mockMvc.perform(get("/api/tasks/41")
                        .principal(authentication(21L, Role.STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedEmployee.id").value(12));

        verify(taskService).getTask(41L, 21L, Role.STAFF);
    }

    @Test
    void updateTaskReturnsCanonicalDto() throws Exception {
        when(taskService.updateTask(any(Long.class), any(UpdateTaskRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/tasks/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void deleteCancelsAndReturnsTaskResponse() throws Exception {
        when(taskService.cancelTask(41L)).thenReturn(response(TaskStatus.CANCELLED));

        mockMvc.perform(delete("/api/tasks/41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void completePassesAuthenticatedIdentity() throws Exception {
        when(taskService.completeTask(41L, 21L, Role.STAFF))
                .thenReturn(response(TaskStatus.COMPLETED));

        mockMvc.perform(patch("/api/tasks/41/complete")
                        .principal(authentication(21L, Role.STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void assignmentSupportsAssignAndUnassignBody() throws Exception {
        when(taskService.assignTask(41L, 12L)).thenReturn(response());
        mockMvc.perform(patch("/api/tasks/41/assigned-employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":12}
                                """))
                .andExpect(status().isOk());

        when(taskService.assignTask(41L, null)).thenReturn(response(TaskStatus.OPEN));
        mockMvc.perform(patch("/api/tasks/41/assigned-employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(taskService).assignTask(41L, 12L);
        verify(taskService).assignTask(41L, null);
    }

    @Test
    void assignedListPassesSelfIdentityAndFilters() throws Exception {
        when(taskService.listAssignedTasks(12L, TaskStatus.ASSIGNED, TaskPriority.HIGH, false, new PageCriteria(0, 20, "createdAt,desc"), 21L, Role.STAFF)).thenReturn(new PagedResponse<>(List.of(response()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/tasks/assigned/12")
                        .principal(authentication(21L, Role.STAFF))
                        .param("status", "ASSIGNED")
                        .param("priority", "HIGH")
                        .param("overdue", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(41));
    }

    @Test
    void missingTaskReturnsCanonical404() throws Exception {
        when(taskService.getTask(99L, 3L, Role.ADMIN))
                .thenThrow(new ResourceNotFoundException("Task not found with id 99."));

        mockMvc.perform(get("/api/tasks/99")
                        .principal(authentication(3L, Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String validCreateJson() {
        return """
                {
                  "title": "Inspect room",
                  "description": "Check readiness.",
                  "departmentId": 3,
                  "assignedEmployeeId": 12,
                  "roomId": 218,
                  "eventId": 7,
                  "priority": "HIGH",
                  "dueAt": "2030-10-04T15:00:00"
                }
                """;
    }

    private String validUpdateJson() {
        return """
                {
                  "title": "Inspect room",
                  "description": "Check readiness.",
                  "departmentId": 3,
                  "assignedEmployeeId": 12,
                  "roomId": 218,
                  "eventId": 7,
                  "priority": "HIGH",
                  "status": "ASSIGNED",
                  "dueAt": "2030-10-04T15:00:00"
                }
                """;
    }

    private UsernamePasswordAuthenticationToken authentication(Long id, Role role) {
        User user = new User("Test User", role.name().toLowerCase() + "@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        RockeyUserPrincipal principal = new RockeyUserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private TaskResponse response() {
        return response(TaskStatus.ASSIGNED);
    }

    private TaskResponse response(TaskStatus status) {
        return new TaskResponse(
                41L,
                "Inspect room",
                "Check readiness.",
                status,
                TaskPriority.HIGH,
                new DepartmentSummary(3L, "Housekeeping"),
                status == TaskStatus.OPEN ? null : new TaskEmployeeSummary(12L, "Worker"),
                new TaskRoomSummary(218L, "218"),
                new TaskEventSummary(
                        7L,
                        "Leadership Conference",
                        LocalDateTime.of(2030, 10, 10, 9, 0),
                        EventStatus.OPEN
                ),
                LocalDateTime.of(2026, 10, 3, 9, 0),
                LocalDateTime.of(2030, 10, 4, 15, 0),
                status == TaskStatus.COMPLETED
                        ? LocalDateTime.of(2026, 10, 3, 12, 0)
                        : null,
                LocalDateTime.of(2026, 10, 3, 10, 0)
        );
    }
}
