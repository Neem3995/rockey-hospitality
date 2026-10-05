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

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("SELECT employee.department.id FROM Employee employee WHERE employee.id = :id")
    Optional<Long> findDepartmentIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT employee FROM Employee employee WHERE employee.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT employee.id FROM Employee employee
            WHERE employee.status = :status AND employee.department.active = TRUE
              AND LOWER(employee.department.name) = LOWER(:name)
            """)
    List<Long> findActiveRecipientIdsByDepartment(@Param("name") String name,
                                                 @Param("status") EmployeeStatus status);

    @Query("""
            SELECT employee.id FROM Employee employee
            WHERE employee.status = :status AND employee.department.active = TRUE
              AND employee.user.role = :role AND employee.user.status = :userStatus
            """)
    List<Long> findActiveAdminRecipientIds(@Param("status") EmployeeStatus status,
                                          @Param("role") Role role,
                                          @Param("userStatus") UserStatus userStatus);

    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    Page<Employee> findByStatus(EmployeeStatus status, Pageable pageable);

    Page<Employee> findByDepartmentIdAndStatus(
            Long departmentId,
            EmployeeStatus status,
            Pageable pageable
    );

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByDepartmentIdAndStatus(Long departmentId, EmployeeStatus status);

    Optional<Employee> findByUserId(Long userId);
}
