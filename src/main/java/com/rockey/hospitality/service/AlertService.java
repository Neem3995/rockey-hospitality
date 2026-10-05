package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.alert.AlertEmployeeSummary;
import com.rockey.hospitality.dto.alert.AlertResponse;
import com.rockey.hospitality.dto.alert.AlertTaskSummary;
import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.common.PageCriteria;
import com.rockey.hospitality.dto.alert.AlertSearchCriteria;
import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.AlertRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Service
public class AlertService {

    private static final Set<String> SORT_FIELDS = Set.of("type", "severity", "status", "createdAt", "readAt", "resolvedAt");
    private final AlertRepository alertRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public AlertService(AlertRepository alertRepository, EmployeeRepository employeeRepository, Clock clock) {
        this.alertRepository = alertRepository;
        this.employeeRepository = employeeRepository;
        this.clock = clock.withZone(ZoneId.systemDefault());
    }

    @Transactional(readOnly = true)
    public PagedResponse<AlertResponse> listAlerts(AlertSearchCriteria criteria, PageCriteria pagination,
                                                 Long userId, Role role) {
        Long employeeId = criteria.employeeId();
        AlertType type = criteria.type();
        AlertStatus status = criteria.status();
        ensureViewer(role);
        if (employeeId != null && employeeId <= 0) throw new BadRequestException("Employee filter must be positive.");
        Long scope = employeeId;
        if (role == Role.STAFF) {
            Long ownId = findActiveEmployee(userId).getId();
            if (employeeId != null && !employeeId.equals(ownId)) {
                throw new ForbiddenException("STAFF may view only its own alerts.");
            }
            scope = ownId;
        }
        Page<Alert> alerts = alertRepository.search(scope, type, status, AlertStatus.RESOLVED,
                pageRequest(pagination.page(), pagination.size(), pagination.sort()));
        return new PagedResponse<>(alerts.getContent().stream().map(this::toResponse).toList(),
                alerts.getNumber(), alerts.getSize(), alerts.getTotalElements(), alerts.getTotalPages(), alerts.isLast());
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlert(Long id, Long userId, Role role) {
        ensureViewer(role);
        Alert alert = alertRepository.findById(id).orElseThrow(() -> notFound(id));
        if (role == Role.STAFF) ensureOwner(alert, userId);
        return toResponse(alert);
    }

    @Transactional
    public AlertResponse markRead(Long id, Long userId, Role role) {
        ensureViewer(role);
        Alert alert = findForUpdate(id);
        // ADMIN oversight does not grant permission to mark someone else's alert READ.
        ensureOwner(alert, userId);
        if (alert.getStatus() != AlertStatus.UNREAD) {
            throw new ConflictException("Only UNREAD alerts may transition to READ.");
        }
        alert.markRead(LocalDateTime.now(clock));
        return toResponse(alertRepository.save(alert));
    }

    @Transactional
    public void resolveAlert(Long id, Long userId, Role role) {
        ensureViewer(role);
        Alert alert = findForUpdate(id);
        if (role == Role.STAFF) ensureOwner(alert, userId);
        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new ConflictException("Alert is already resolved.");
        }
        alert.resolve(LocalDateTime.now(clock));
        alertRepository.save(alert);
    }

    private void ensureViewer(Role role) {
        if (role != Role.STAFF && role != Role.ADMIN) throw new ForbiddenException("Alert access is forbidden.");
    }

    private Employee findActiveEmployee(Long userId) {
        Employee employee = employeeRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("An active employee profile is required."));
        if (employee.getStatus() != EmployeeStatus.ACTIVE || !Boolean.TRUE.equals(employee.getDepartment().getActive())) {
            throw new ForbiddenException("An active employee profile is required.");
        }
        return employee;
    }

    private void ensureOwner(Alert alert, Long userId) {
        if (!findActiveEmployee(userId).getId().equals(alert.getEmployee().getId())) {
            throw new ForbiddenException("This alert belongs to another employee.");
        }
    }

    private Alert findForUpdate(Long id) {
        return alertRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Alert not found with id " + id + ".");
    }

    private PageRequest pageRequest(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Invalid page or size; maximum size is 100.");
        String[] parts = sort == null || sort.isBlank() ? new String[]{"createdAt", "desc"} : sort.split(",", -1);
        if (parts.length > 2 || !SORT_FIELDS.contains(parts[0])) throw new BadRequestException("Alert sort is invalid.");
        try {
            return PageRequest.of(page, size, Sort.by(parts.length == 1 ? Sort.Direction.ASC : Sort.Direction.fromString(parts[1]), parts[0]));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Alert sort direction is invalid.");
        }
    }

    private AlertResponse toResponse(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getType(), alert.getMessage(), alert.getSeverity(),
                alert.getStatus(), new AlertEmployeeSummary(alert.getEmployee().getId(), alert.getEmployee().getName()),
                alert.getTask() == null ? null : new AlertTaskSummary(alert.getTask().getId(), alert.getTask().getTitle()),
                alert.getSourceKey(), alert.getCreatedAt(), alert.getReadAt(), alert.getResolvedAt());
    }
}
