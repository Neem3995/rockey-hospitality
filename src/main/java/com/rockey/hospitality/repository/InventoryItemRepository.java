package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.InventoryItem;
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
 * Spring Data JPA supplies standard persistence operations for InventoryItem entities through JpaRepository.
 * Domain services use the methods below for filtered reads, eligibility checks, and locked writes where declared.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    /**
     * Finds active stock rows at or below their threshold for alert reconciliation.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("SELECT item FROM InventoryItem item WHERE item.active = TRUE AND item.quantity <= item.reorderThreshold")
    List<InventoryItem> findInventoryAlertSources();

    /**
     * Pages optional Department and active filters, fetching each Department for DTO mapping.
     * A separate count query preserves correct page totals.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query(value = """
            SELECT item FROM InventoryItem item JOIN FETCH item.department
            WHERE (:departmentId IS NULL OR item.department.id = :departmentId)
              AND (:active IS NULL OR item.active = :active)
            """, countQuery = """
            SELECT COUNT(item) FROM InventoryItem item
            WHERE (:departmentId IS NULL OR item.department.id = :departmentId)
              AND (:active IS NULL OR item.active = :active)
            """)
    Page<InventoryItem> search(
            // Binds this argument as the named departmentId query parameter, not interpolated query text.
            @Param("departmentId") Long departmentId,
                               // Binds this argument as the named active query parameter, not interpolated query text.
                               @Param("active") Boolean active, Pageable pageable);

    /**
     * Checks global case-insensitive SKU uniqueness before stock creation.
     */
    boolean existsBySkuIgnoreCase(String sku);

    /**
     * Checks whether active stock blocks Department deactivation.
     */
    boolean existsByDepartmentIdAndActiveTrue(Long departmentId);

    /**
     * Locks one stock row for update or soft deactivation.
     */
    // Acquires a PESSIMISTIC_WRITE database row lock until the caller's transaction ends.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("SELECT item FROM InventoryItem item WHERE item.id = :id")
    Optional<InventoryItem> findByIdForUpdate(
            // Binds this argument as the named id query parameter, not interpolated query text.
            @Param("id") Long id);
}
