package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Department;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT department FROM Department department WHERE department.id = :id")
    Optional<Department> findByIdForUpdate(@Param("id") Long id);

    List<Department> findAllByOrderByNameAsc();

    List<Department> findByActiveOrderByNameAsc(Boolean active);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
