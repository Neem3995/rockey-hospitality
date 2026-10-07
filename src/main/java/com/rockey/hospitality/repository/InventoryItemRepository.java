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
 * STUDY NOTE: A Repository is the data-access layer a Service uses to reach database data.
 * JpaRepository lets Spring Data supply standard create/read/update/delete methods without writing basic
 * SQL.
 * InventoryService and automation use scoped pages, SKU checks, Department guards and low-stock sources.
 * Spring creates this interface's implementation and sends its queries through JPA/Hibernate to MySQL.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    // Repository study key: findBy/existsBy/countBy names are interpreted by Spring Data as queries.
    // @Query supplies fixed JPQL (entity/field-based query text), not string-interpolated user input.
    // @Param binds a Java argument to a named query value instead of inserting it into query text.
    // @Lock(PESSIMISTIC_WRITE) keeps a database row locked until the caller's transaction ends to serialize
    // conflicting changes.

    /**
     * Finds active stock rows at or below their threshold for alert reconciliation.
     */
    @Query("SELECT item FROM InventoryItem item WHERE item.active = TRUE AND item.quantity <= item.reorderThreshold")
    List<InventoryItem> findInventoryAlertSources();

    /**
     * Pages optional Department and active filters, fetching each Department for DTO mapping.
     * A separate count query preserves correct page totals.
     */
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
            @Param("departmentId") Long departmentId,
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT item FROM InventoryItem item WHERE item.id = :id")
    Optional<InventoryItem> findByIdForUpdate(
            @Param("id") Long id);
}
