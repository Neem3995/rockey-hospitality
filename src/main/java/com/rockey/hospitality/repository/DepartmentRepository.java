package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Department;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA supplies standard persistence operations for Department entities through JpaRepository.
 * Domain services use the methods below for filtered reads, eligibility checks, and locked writes where declared.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * Locks one Department so reference assignment and deactivation checks share the same serialization point.
     */
    // Acquires a PESSIMISTIC_WRITE database row lock until the caller's transaction ends.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Creation/transfer and deactivation share this lock to keep active-reference checks valid.
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("SELECT department FROM Department department WHERE department.id = :id")
    Optional<Department> findByIdForUpdate(
            // Binds this argument as the named id query parameter, not interpolated query text.
            @Param("id") Long id);

    /**
     * Returns all Departments alphabetically through a Spring Data derived query.
     */
    List<Department> findAllByOrderByNameAsc();

    /**
     * Returns Departments matching the requested active flag in alphabetical order.
     */
    List<Department> findByActiveOrderByNameAsc(Boolean active);

    /**
     * Checks case-insensitive name availability for creation.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Checks case-insensitive name availability while excluding the edited Department.
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
