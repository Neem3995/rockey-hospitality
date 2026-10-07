package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * STUDY NOTE: A Repository is the data-access layer a Service uses to reach database data.
 * JpaRepository lets Spring Data supply standard create/read/update/delete methods without writing basic
 * SQL.
 * Employee and operational services use profile/Department lookups, eligibility checks and recipient
 * queries.
 * Spring creates this interface's implementation and sends its queries through JPA/Hibernate to MySQL.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // Repository study key: findBy/existsBy/countBy names are interpreted by Spring Data as queries.
    // @Query supplies fixed JPQL (entity/field-based query text), not string-interpolated user input.
    // @Param binds a Java argument to a named query value instead of inserting it into query text.
    // @Lock(PESSIMISTIC_WRITE) keeps a database row locked until the caller's transaction ends to serialize
    // conflicting changes.

    /**
     * Reads only the Employee's Department ID so deactivation can acquire Department before Employee locks.
     */
    @Query("SELECT employee.department.id FROM Employee employee WHERE employee.id = :id")
    Optional<Long> findDepartmentIdById(
            @Param("id") Long id);

    /**
     * Locks one Employee for profile changes, assignment eligibility, or alert reconciliation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT employee FROM Employee employee WHERE employee.id = :id")
    Optional<Employee> findByIdForUpdate(
            @Param("id") Long id);

    /**
     * Finds active employees in an active named Department, matching its name without regard to case.
     */
    @Query("""
            SELECT employee.id FROM Employee employee
            WHERE employee.status = :status AND employee.department.active = TRUE
              AND LOWER(employee.department.name) = LOWER(:name)
            """)
    List<Long> findActiveRecipientIdsByDepartment(
            @Param("name") String name,
                                                 @Param("status") Employee.Status status);

    /**
     * Finds active employees linked to active ADMIN Users in active Departments for inventory-alert fallback.
     */
    @Query("""
            SELECT employee.id FROM Employee employee
            WHERE employee.status = :status AND employee.department.active = TRUE
              AND employee.user.role = :role AND employee.user.status = :userStatus
            """)
    List<Long> findActiveAdminRecipientIds(
            @Param("status") Employee.Status status,
                                          @Param("role") User.Role role,
                                          @Param("userStatus") User.Status userStatus);

    /**
     * Pages Employee profiles belonging to one Department.
     */
    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    /**
     * Pages Employee profiles with the requested lifecycle status.
     */
    Page<Employee> findByStatus(Employee.Status status, Pageable pageable);

    /**
     * Pages Employee profiles satisfying both Department and lifecycle filters.
     */
    Page<Employee> findByDepartmentIdAndStatus(
            Long departmentId,
            Employee.Status status,
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
    boolean existsByDepartmentIdAndStatus(Long departmentId, Employee.Status status);

    /**
     * Finds the optional unique profile linked to an authenticated User.
     */
    Optional<Employee> findByUserId(Long userId);
}
