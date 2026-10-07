package com.rockey.hospitality.service;

import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.InventoryItemRepository;
import com.rockey.hospitality.repository.TaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    private DepartmentService departmentService;

    @BeforeEach
    void setUp() {
        departmentService = new DepartmentService(
                departmentRepository,
                employeeRepository,
                taskRepository,
                inventoryItemRepository
        );
    }

    @Test
    void deactivateRejectsDepartmentWithNonTerminalTasks() {
        Department department = department(1L, "Housekeeping", true);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        when(taskRepository.existsByDepartmentIdAndStatusIn(
                1L,
                TaskService.NON_TERMINAL_STATUSES
        )).thenReturn(true);

        assertThatThrownBy(() -> departmentService.deactivateDepartment(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Department cannot be deactivated while non-terminal tasks exist.");

        assertThat(department.getActive()).isTrue();
        verify(departmentRepository, never()).save(department);
    }

    @Test
    void listDepartmentsWithoutFilterReturnsAllDepartmentsInRepositoryOrder() {
        Department housekeeping = department(1L, "Housekeeping", true);
        Department maintenance = department(2L, "Maintenance", false);
        when(departmentRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(housekeeping, maintenance));

        List<Department> result = departmentService.listDepartments(null);

        assertThat(result).containsExactly(housekeeping, maintenance);
        verify(departmentRepository).findAllByOrderByNameAsc();
    }

    @Test
    void listDepartmentsWithActiveFilterUsesActiveRepositoryQuery() {
        Department housekeeping = department(1L, "Housekeeping", true);
        when(departmentRepository.findByActiveOrderByNameAsc(true))
                .thenReturn(List.of(housekeeping));

        List<Department> result = departmentService.listDepartments(true);

        assertThat(result).containsExactly(housekeeping);
        verify(departmentRepository).findByActiveOrderByNameAsc(true);
    }

    @Test
    void createDepartmentTrimsNameAndSavesDepartment() {
        when(departmentRepository.existsByNameIgnoreCase("Housekeeping")).thenReturn(false);
        when(departmentRepository.save(org.mockito.ArgumentMatchers.any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Department result = departmentService.createDepartment(
                "  Housekeeping  ",
                "Room turnover and inspection"
        );

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Housekeeping");
        assertThat(result.getDescription()).isEqualTo("Room turnover and inspection");
        assertThat(result.getActive()).isTrue();
    }

    @Test
    void createDepartmentRejectsNormalizedDuplicateName() {
        when(departmentRepository.existsByNameIgnoreCase("Housekeeping")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(" Housekeeping ", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Department name already exists.");

        verify(departmentRepository, never())
                .save(org.mockito.ArgumentMatchers.any(Department.class));
    }

    @Test
    void getDepartmentReturnsExistingDepartment() {
        Department housekeeping = department(1L, "Housekeeping", true);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(housekeeping));

        Department result = departmentService.getDepartment(1L);

        assertThat(result).isSameAs(housekeeping);
    }

    @Test
    void getDepartmentRejectsMissingDepartment() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getDepartment(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Department not found with id 99.");
    }

    @Test
    void updateDepartmentChangesOnlyAllowedFields() {
        Department department = department(1L, "Housekeeping", true);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByNameIgnoreCaseAndIdNot("Guest Services", 1L))
                .thenReturn(false);
        when(departmentRepository.save(department)).thenReturn(department);

        Department result = departmentService.updateDepartment(
                1L,
                "  Guest Services  ",
                "Front desk and guest support"
        );

        assertThat(result.getName()).isEqualTo("Guest Services");
        assertThat(result.getDescription()).isEqualTo("Front desk and guest support");
        assertThat(result.getActive()).isTrue();
        verify(departmentRepository).save(department);
    }

    @Test
    void updateDepartmentRejectsNormalizedDuplicateName() {
        Department department = department(1L, "Housekeeping", true);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByNameIgnoreCaseAndIdNot("Events", 1L))
                .thenReturn(true);

        assertThatThrownBy(() -> departmentService.updateDepartment(1L, " Events ", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Department name already exists.");

        verify(departmentRepository, never()).save(department);
    }

    @Test
    void deactivateDepartmentMarksDepartmentInactiveWithoutDeletingIt() {
        Department department = department(1L, "Housekeeping", true);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);

        departmentService.deactivateDepartment(1L);

        assertThat(department.getActive()).isFalse();
        verify(departmentRepository).save(department);
        verify(departmentRepository, never()).delete(department);
        verify(inventoryItemRepository).existsByDepartmentIdAndActiveTrue(1L);
    }

    @Test
    void deactivateDepartmentRejectsMissingDepartment() {
        when(departmentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.deactivateDepartment(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Department not found with id 99.");
    }

    @Test
    void deactivateDepartmentRejectsActiveEmployeeReference() {
        Department department = department(1L, "Housekeeping", true);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        when(employeeRepository.existsByDepartmentIdAndStatus(
                1L,
                Employee.Status.ACTIVE
        )).thenReturn(true);

        assertThatThrownBy(() -> departmentService.deactivateDepartment(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage(
                        "Department cannot be deactivated while active employees are assigned."
                );

        assertThat(department.getActive()).isTrue();
        verify(departmentRepository, never()).save(department);
    }

    @Test
    void deactivateDepartmentRejectsActiveInventoryWithoutChangingHistory() {
        Department department = department(1L, "Housekeeping", true);
        when(departmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        when(inventoryItemRepository.existsByDepartmentIdAndActiveTrue(1L)).thenReturn(true);

        assertThatThrownBy(() -> departmentService.deactivateDepartment(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Department cannot be deactivated while active inventory exists.");

        assertThat(department.getActive()).isTrue();
        verify(departmentRepository, never()).save(department);
        verify(inventoryItemRepository, never()).deleteAll();
    }

    private Department department(Long id, String name, boolean active) {
        Department department = new Department(name, null);
        ReflectionTestUtils.setField(department, "id", id);
        ReflectionTestUtils.setField(department, "active", active);
        return department;
    }
}
