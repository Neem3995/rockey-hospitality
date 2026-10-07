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
 * STUDY NOTE: A Repository is the data-access layer a Service uses to reach database data.
 * JpaRepository lets Spring Data supply standard create/read/update/delete methods without writing basic
 * SQL.
 * Department and other services share locked lookups so assignment cannot bypass deactivation checks.
 * Spring creates this interface's implementation and sends its queries through JPA/Hibernate to MySQL.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // Repository study key: findBy/existsBy/countBy names are interpreted by Spring Data as queries.
    // @Query supplies fixed JPQL (entity/field-based query text), not string-interpolated user input.
    // @Param binds a Java argument to a named query value instead of inserting it into query text.
    // @Lock(PESSIMISTIC_WRITE) keeps a database row locked until the caller's transaction ends to serialize
    // conflicting changes.

    /**
     * Locks one Department so reference assignment and deactivation checks share the same serialization point.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Creation/transfer and deactivation share this lock to keep active-reference checks valid.
    @Query("SELECT department FROM Department department WHERE department.id = :id")
    Optional<Department> findByIdForUpdate(
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
