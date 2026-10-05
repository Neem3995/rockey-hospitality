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

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Query("SELECT item FROM InventoryItem item WHERE item.active = TRUE AND item.quantity <= item.reorderThreshold")
    List<InventoryItem> findInventoryAlertSources();

    @Query(value = """
            SELECT item FROM InventoryItem item JOIN FETCH item.department
            WHERE (:departmentId IS NULL OR item.department.id = :departmentId)
              AND (:active IS NULL OR item.active = :active)
            """, countQuery = """
            SELECT COUNT(item) FROM InventoryItem item
            WHERE (:departmentId IS NULL OR item.department.id = :departmentId)
              AND (:active IS NULL OR item.active = :active)
            """)
    Page<InventoryItem> search(@Param("departmentId") Long departmentId,
                               @Param("active") Boolean active, Pageable pageable);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsByDepartmentIdAndActiveTrue(Long departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT item FROM InventoryItem item WHERE item.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") Long id);
}
