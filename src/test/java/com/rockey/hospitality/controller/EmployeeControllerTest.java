package com.rockey.hospitality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.dto.AuthDtos.DepartmentSummary;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.EmployeeDtos.CreateEmployeeRequest;
import com.rockey.hospitality.dto.EmployeeDtos.EmployeeResponse;
import com.rockey.hospitality.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.service.EmployeeService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    @Mock
    private EmployeeService employeeService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new EmployeeController(employeeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.destroy();
    }

    @Test
    void listEmployeesReturnsPagedResponseAndPassesFilters() throws Exception {
        when(employeeService.listEmployees(
                3L,
                Employee.Status.ACTIVE,
                1,
                5,
                "createdAt,desc"
        )).thenReturn(new PagedResponse<>(
                List.of(response()),
                1,
                5,
                6,
                2,
                true
        ));

        mockMvc.perform(get("/api/employees")
                        .param("departmentId", "3")
                        .param("status", "ACTIVE")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(12))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void createEmployeeReturns201WithoutSensitiveAccountFields() throws Exception {
        when(employeeService.createEmployee(any(CreateEmployeeRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.userId").value(21))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void invalidLoginProvisioningReturnsCanonical400() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Worker Name",
                                  "email": "worker@example.test",
                                  "departmentId": 3,
                                  "jobRole": "Room Attendant",
                                  "createLogin": true,
                                  "loginEmail": "login@example.test",
                                  "temporaryPassword": "temporary-password",
                                  "securityRole": "USER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.loginConfigurationValid").exists());
    }

    @Test
    void updateEmployeeReturnsUpdatedDto() throws Exception {
        when(employeeService.updateEmployee(any(Long.class), any(UpdateEmployeeRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/employees/12")
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.id").value(3));
    }

    @Test
    void deactivateEmployeeReturns204() throws Exception {
        mockMvc.perform(delete("/api/employees/12"))
                .andExpect(status().isNoContent());

        verify(employeeService).deactivateEmployee(12L);
    }

    @Test
    void missingDepartmentReturnsCanonical404() throws Exception {
        when(employeeService.createEmployee(any(CreateEmployeeRequest.class)))
                .thenThrow(new ResourceNotFoundException("Department not found with id 3."));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void invalidPaginationReturnsCanonical400() throws Exception {
        when(employeeService.listEmployees(null, null, 0, 101, "name,asc"))
                .thenThrow(new BadRequestException("Page size must be between 1 and 100."));

        mockMvc.perform(get("/api/employees").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private String validCreateJson() {
        return """
                {
                  "name": "Worker Name",
                  "email": "worker@example.test",
                  "departmentId": 3,
                  "jobRole": "Room Attendant",
                  "createLogin": true,
                  "loginEmail": "login@example.test",
                  "temporaryPassword": "temporary-password",
                  "securityRole": "STAFF"
                }
                """;
    }

    private EmployeeResponse response() {
        return new EmployeeResponse(
                12L,
                21L,
                "Worker Name",
                "worker@example.test",
                new DepartmentSummary(3L, "Housekeeping"),
                "Room Attendant",
                Employee.Status.ACTIVE,
                LocalDateTime.of(2026, 10, 3, 9, 0),
                LocalDateTime.of(2026, 10, 3, 9, 0)
        );
    }
}
