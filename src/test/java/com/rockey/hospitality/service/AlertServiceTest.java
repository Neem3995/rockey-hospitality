package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.common.PageCriteria;
import com.rockey.hospitality.dto.alert.AlertSearchCriteria;
import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.AlertRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {
    @Mock private AlertRepository alerts;
    @Mock private EmployeeRepository employees;
    private AlertService service;
    private Employee employee;
    private Alert alert;
    private final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        service = new AlertService(alerts, employees, Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneOffset.UTC));
        Department department = new Department("Housekeeping", null);
        employee = new Employee("Worker", "worker@example.test", department, "Attendant", null);
        ReflectionTestUtils.setField(employee, "id", 12L);
        alert = new Alert(AlertType.ROOM, "Room is not ready.", employee, null, "ROOM:1:ARRIVAL_NOT_READY", now.minusMinutes(5));
        ReflectionTestUtils.setField(alert, "id", 501L);
    }

    @Test
    void staffListForcesOwnScopeAndDefaultActiveStatuses() {
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee));
        when(alerts.search(eq(12L), isNull(), isNull(), eq(AlertStatus.RESOLVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alert)));
        var page = service.listAlerts(new AlertSearchCriteria(null, null, null), new PageCriteria(0, 20, null), 21L, Role.STAFF);
        assertThat(page.getContent()).hasSize(1);
        verify(alerts).search(12L, null, null, AlertStatus.RESOLVED,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void adminMayFilterOtherEmployeeAndResolvedHistory() {
        when(alerts.search(eq(12L), eq(AlertType.ROOM), eq(AlertStatus.RESOLVED), eq(AlertStatus.RESOLVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alert), PageRequest.of(1, 5), 6));
        var page = service.listAlerts(new AlertSearchCriteria(12L, AlertType.ROOM, AlertStatus.RESOLVED), new PageCriteria(1, 5, "createdAt,asc"), 3L, Role.ADMIN);
        assertThat(page.getTotalElements()).isEqualTo(6);
        verify(alerts).search(12L, AlertType.ROOM, AlertStatus.RESOLVED, AlertStatus.RESOLVED,
                PageRequest.of(1, 5, Sort.by("createdAt")));
    }

    @Test
    void adminDefaultListIsUnscoped() {
        when(alerts.search(isNull(), isNull(), isNull(), eq(AlertStatus.RESOLVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.listAlerts(new AlertSearchCriteria(null, null, null), new PageCriteria(0, 20, "createdAt"), 3L, Role.ADMIN).getContent()).isEmpty();
    }

    @Test
    void staffCannotFilterAnotherEmployee() {
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee));
        AlertSearchCriteria filterCriteria15 = new AlertSearchCriteria(13L, null, null);
        PageCriteria paginationCriteria16 = new PageCriteria(0, 20, null);
        assertThatThrownBy(() -> service.listAlerts(filterCriteria15, paginationCriteria16, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(alerts);
    }

    @Test
    void staffCanReadOwnAlertWithSafeShallowSummary() {
        when(alerts.findById(501L)).thenReturn(Optional.of(alert));
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee));
        var result = service.getAlert(501L, 21L, Role.STAFF);
        assertThat(result.getEmployee().getId()).isEqualTo(12L);
        assertThat(result.getTask()).isNull();
        assertThat(result.getSourceKey()).isEqualTo("ROOM:1:ARRIVAL_NOT_READY");
    }

    @Test
    void staffCannotRetrieveOrResolveAnotherRecipientsAlert() {
        Employee other = new Employee("Other", "other@example.test", employee.getDepartment(), "Attendant", null);
        ReflectionTestUtils.setField(other, "id", 13L);
        when(employees.findByUserId(22L)).thenReturn(Optional.of(other));
        when(alerts.findById(501L)).thenReturn(Optional.of(alert));
        when(alerts.findByIdForUpdate(501L)).thenReturn(Optional.of(alert));
        assertThatThrownBy(() -> service.getAlert(501L, 22L, Role.STAFF)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.resolveAlert(501L, 22L, Role.STAFF)).isInstanceOf(ForbiddenException.class);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.UNREAD);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STAFF", "ADMIN"})
    void recipientCanMarkReadUsingServerClock(Role role) {
        when(alerts.findByIdForUpdate(501L)).thenReturn(Optional.of(alert));
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee));
        when(alerts.save(alert)).thenReturn(alert);
        var result = service.markRead(501L, 21L, role);
        assertThat(result.getStatus()).isEqualTo(AlertStatus.READ);
        assertThat(result.getReadAt()).isEqualTo(now);
        assertThat(result.getResolvedAt()).isNull();
    }

    @Test
    void adminCannotMarkAnotherEmployeesAlertRead() {
        Employee admin = new Employee("Admin", "admin@example.test", employee.getDepartment(), "Management", null);
        ReflectionTestUtils.setField(admin, "id", 3L);
        when(employees.findByUserId(3L)).thenReturn(Optional.of(admin));
        when(alerts.findByIdForUpdate(501L)).thenReturn(Optional.of(alert));
        assertThatThrownBy(() -> service.markRead(501L, 3L, Role.ADMIN)).isInstanceOf(ForbiddenException.class);
        verify(alerts, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = AlertStatus.class, names = {"READ", "RESOLVED"})
    void invalidReadTransitionReturns409(AlertStatus status) {
        ReflectionTestUtils.setField(alert, "status", status);
        when(alerts.findByIdForUpdate(501L)).thenReturn(Optional.of(alert));
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee));
        assertThatThrownBy(() -> service.markRead(501L, 21L, Role.STAFF)).isInstanceOf(ConflictException.class);
        verify(alerts, never()).save(any());
    }

    @Test
    void resolvingUnreadSetsReadAndResolvedTimestampsWithoutDeletion() {
        when(alerts.findByIdForUpdate(501L)).thenReturn(Optional.of(alert));
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee));
        service.resolveAlert(501L, 21L, Role.STAFF);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getReadAt()).isEqualTo(now);
        assertThat(alert.getResolvedAt()).isEqualTo(now);
        assertThat(alert.getEmployee()).isSameAs(employee);
        verify(alerts).save(alert);
        verify(alerts, never()).delete(any());
    }

    @Test
    void adminMayResolveOtherAlertAndPreserveFirstReadTime() {
        alert.markRead(now.minusMinutes(2));
        when(alerts.findByIdForUpdate(501L)).thenReturn(Optional.of(alert));
        service.resolveAlert(501L, 3L, Role.ADMIN);
        assertThat(alert.getReadAt()).isEqualTo(now.minusMinutes(2));
        assertThat(alert.getResolvedAt()).isEqualTo(now);
        verifyNoInteractions(employees);
    }

    @Test
    void adminMayRetrieveOtherResolvedHistory() {
        alert.resolve(now);
        when(alerts.findById(501L)).thenReturn(Optional.of(alert));
        assertThat(service.getAlert(501L, 3L, Role.ADMIN).getStatus()).isEqualTo(AlertStatus.RESOLVED);
    }

    @Test
    void repeatedResolveReturns409AndDoesNotRewriteHistory() {
        alert.resolve(now.minusMinutes(1));
        when(alerts.findByIdForUpdate(501L)).thenReturn(Optional.of(alert));
        assertThatThrownBy(() -> service.resolveAlert(501L, 3L, Role.ADMIN)).isInstanceOf(ConflictException.class);
        assertThat(alert.getResolvedAt()).isEqualTo(now.minusMinutes(1));
        verify(alerts, never()).save(any());
    }

    @Test
    void missingAlertReturns404ForEveryIdOperation() {
        assertThatThrownBy(() -> service.getAlert(99L, 3L, Role.ADMIN)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.markRead(99L, 3L, Role.ADMIN)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.resolveAlert(99L, 3L, Role.ADMIN)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inactiveStaffCannotListAlerts() {
        employee.deactivate();
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee));
        AlertSearchCriteria filterCriteria13 = new AlertSearchCriteria(null, null, null);
        PageCriteria paginationCriteria14 = new PageCriteria(0, 20, null);
        assertThatThrownBy(() -> service.listAlerts(filterCriteria13, paginationCriteria14, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inactiveDepartmentPreventsStaffAccess() {
        employee.getDepartment().deactivate();
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee));
        AlertSearchCriteria filterCriteria11 = new AlertSearchCriteria(null, null, null);
        PageCriteria paginationCriteria12 = new PageCriteria(0, 20, null);
        assertThatThrownBy(() -> service.listAlerts(filterCriteria11, paginationCriteria12, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unlinkedStaffIsRejected() {
        AlertSearchCriteria filterCriteria9 = new AlertSearchCriteria(null, null, null);
        PageCriteria paginationCriteria10 = new PageCriteria(0, 20, null);
        assertThatThrownBy(() -> service.listAlerts(filterCriteria9, paginationCriteria10, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userCannotAccessAlertOperations() {
        AlertSearchCriteria filterCriteria7 = new AlertSearchCriteria(null, null, null);
        PageCriteria paginationCriteria8 = new PageCriteria(0, 20, null);
        assertThatThrownBy(() -> service.listAlerts(filterCriteria7, paginationCriteria8, 31L, Role.USER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.getAlert(501L, 31L, Role.USER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.markRead(501L, 31L, Role.USER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.resolveAlert(501L, 31L, Role.USER)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(alerts, employees);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void invalidPaginationReturns400(int page, int size) {
        AlertSearchCriteria filterCriteria5 = new AlertSearchCriteria(null, null, null);
        PageCriteria paginationCriteria6 = new PageCriteria(page, size, null);
        assertThatThrownBy(() -> service.listAlerts(filterCriteria5, paginationCriteria6, 3L, Role.ADMIN))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordHash", "createdAt,wrong", "createdAt,asc,extra", "createdAt,"})
    void invalidSortReturns400(String sort) {
        AlertSearchCriteria filterCriteria3 = new AlertSearchCriteria(null, null, null);
        PageCriteria paginationCriteria4 = new PageCriteria(0, 20, sort);
        assertThatThrownBy(() -> service.listAlerts(filterCriteria3, paginationCriteria4, 3L, Role.ADMIN))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void invalidEmployeeFilterReturns400() {
        AlertSearchCriteria filterCriteria1 = new AlertSearchCriteria(0L, null, null);
        PageCriteria paginationCriteria2 = new PageCriteria(0, 20, null);
        assertThatThrownBy(() -> service.listAlerts(filterCriteria1, paginationCriteria2, 3L, Role.ADMIN))
                .isInstanceOf(BadRequestException.class);
    }
}
