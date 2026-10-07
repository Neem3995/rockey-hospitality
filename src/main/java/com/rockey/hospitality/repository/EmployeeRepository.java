package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

/**
 * Spring Data JPA supplies standard persistence operations for Employee entities through JpaRepository.
 * Domain services use the methods below for filtered reads, eligibility checks, and locked writes where declared.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Reads only the Employee's Department ID so deactivation can acquire Department before Employee locks.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("SELECT employee.department.id FROM Employee employee WHERE employee.id = :id")
    Optional<Long> findDepartmentIdById(
            // Binds this argument as the named id query parameter, not interpolated query text.
            @Param("id") Long id);

    /**
     * Locks one Employee for profile changes, assignment eligibility, or alert reconciliation.
     */
    // Acquires a PESSIMISTIC_WRITE database row lock until the caller's transaction ends.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("SELECT employee FROM Employee employee WHERE employee.id = :id")
    Optional<Employee> findByIdForUpdate(
            // Binds this argument as the named id query parameter, not interpolated query text.
            @Param("id") Long id);

    /**
     * Finds active employees in an active named Department, matching its name without regard to case.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT employee.id FROM Employee employee
            WHERE employee.status = :status AND employee.department.active = TRUE
              AND LOWER(employee.department.name) = LOWER(:name)
            """)
    List<Long> findActiveRecipientIdsByDepartment(
            // Binds this argument as the named name query parameter, not interpolated query text.
            @Param("name") String name,
                                                 // Binds this argument as the named status query parameter, not interpolated query text.
                                                 @Param("status") EmployeeStatus status);

    /**
     * Finds active employees linked to active ADMIN Users in active Departments for inventory-alert fallback.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT employee.id FROM Employee employee
            WHERE employee.status = :status AND employee.department.active = TRUE
              AND employee.user.role = :role AND employee.user.status = :userStatus
            """)
    List<Long> findActiveAdminRecipientIds(
            // Binds this argument as the named status query parameter, not interpolated query text.
            @Param("status") EmployeeStatus status,
                                          // Binds this argument as the named role query parameter, not interpolated query text.
                                          @Param("role") Role role,
                                          // Binds this argument as the named userStatus query parameter, not interpolated query text.
                                          @Param("userStatus") UserStatus userStatus);

    /**
     * Pages Employee profiles belonging to one Department.
     */
    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    /**
     * Pages Employee profiles with the requested lifecycle status.
     */
    Page<Employee> findByStatus(EmployeeStatus status, Pageable pageable);

    /**
     * Pages Employee profiles satisfying both Department and lifecycle filters.
     */
    Page<Employee> findByDepartmentIdAndStatus(
            Long departmentId,
            EmployeeStatus status,
            Pageable pageable
    );

    /**
     * Checks whether a profile email is already used, independently of User login email.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Checks profile-email uniqueness without counting the edited Employee.
     */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Checks for Employees of the requested status before Department deactivation.
     */
    boolean existsByDepartmentIdAndStatus(Long departmentId, EmployeeStatus status);

    /**
     * Finds the optional unique profile linked to an authenticated User.
     */
    Optional<Employee> findByUserId(Long userId);
}
