package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA supplies standard persistence operations for Alert entities through JpaRepository.
 * Domain services use the methods below for filtered reads, eligibility checks, and locked writes where declared.
 */
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * Pages optional recipient/type/status filters; an omitted status excludes RESOLVED alerts, while an explicit status can include history.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT alert FROM Alert alert
            WHERE (:employeeId IS NULL OR alert.employee.id = :employeeId)
              AND (:type IS NULL OR alert.type = :type)
              AND ((:status IS NULL AND alert.status <> :resolved) OR alert.status = :status)
            """)
    Page<Alert> search(
            // Binds this argument as the named employeeId query parameter, not interpolated query text.
            @Param("employeeId") Long employeeId,
            // Binds this argument as the named type query parameter, not interpolated query text.
            @Param("type") AlertType type,
                       // Binds this argument as the named status query parameter, not interpolated query text.
                       @Param("status") AlertStatus status,
            // Binds this argument as the named resolved query parameter, not interpolated query text.
            @Param("resolved") AlertStatus resolved,
                       Pageable pageable);

    /**
     * Locks one Alert for a lifecycle change so concurrent writes serialize within the caller's transaction.
     */
    // Acquires a PESSIMISTIC_WRITE database row lock until the caller's transaction ends.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("SELECT alert FROM Alert alert WHERE alert.id = :id")
    Optional<Alert> findByIdForUpdate(
            // Binds this argument as the named id query parameter, not interpolated query text.
            @Param("id") Long id);

    /**
     * Finds distinct recipients with source-derived unresolved alerts so automation can also resolve conditions that disappeared.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT DISTINCT alert.employee.id FROM Alert alert
            WHERE alert.type = :type AND alert.status IN :statuses AND alert.sourceKey IS NOT NULL
            """)
    List<Long> findUnresolvedRecipientIds(
            // Binds this argument as the named type query parameter, not interpolated query text.
            @Param("type") AlertType type,
                                        // Binds this argument as the named statuses query parameter, not interpolated query text.
                                        @Param("statuses") Collection<AlertStatus> statuses);

    /**
     * Locks a recipient's unresolved source alerts of one type in ID order for reconciliation and duplicate handling.
     */
    // Acquires a PESSIMISTIC_WRITE database row lock until the caller's transaction ends.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT alert FROM Alert alert
            WHERE alert.employee.id = :employeeId AND alert.type = :type
              AND alert.status IN :statuses AND alert.sourceKey IS NOT NULL
            ORDER BY alert.id
            """)
    List<Alert> findUnresolvedForUpdate(
            // Binds this argument as the named employeeId query parameter, not interpolated query text.
            @Param("employeeId") Long employeeId,
                                       // Binds this argument as the named type query parameter, not interpolated query text.
                                       @Param("type") AlertType type,
                                       // Binds this argument as the named statuses query parameter, not interpolated query text.
                                       @Param("statuses") Collection<AlertStatus> statuses);
}
