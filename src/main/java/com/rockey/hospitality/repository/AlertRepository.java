package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Alert;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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
 * AlertService and automation use scoped reads and locked unresolved-alert lookups to preserve recipient
 * history.
 * Spring creates this interface's implementation and sends its queries through JPA/Hibernate to MySQL.
 */
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Repository study key: findBy/existsBy/countBy names are interpreted by Spring Data as queries.
    // @Query supplies fixed JPQL (entity/field-based query text), not string-interpolated user input.
    // @Param binds a Java argument to a named query value instead of inserting it into query text.
    // @Lock(PESSIMISTIC_WRITE) keeps a database row locked until the caller's transaction ends to serialize
    // conflicting changes.

    /**
     * Pages optional recipient/type/status filters; an omitted status excludes RESOLVED alerts, while an explicit status can include history.
     */
    @Query("""
            SELECT alert FROM Alert alert
            WHERE (:employeeId IS NULL OR alert.employee.id = :employeeId)
              AND (:type IS NULL OR alert.type = :type)
              AND ((:status IS NULL AND alert.status <> :resolved) OR alert.status = :status)
            """)
    Page<Alert> search(
            @Param("employeeId") Long employeeId,
            @Param("type") Alert.Type type,
                       @Param("status") Alert.Status status,
            @Param("resolved") Alert.Status resolved,
                       Pageable pageable);

    /**
     * Locks one Alert for a lifecycle change so concurrent writes serialize within the caller's transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT alert FROM Alert alert WHERE alert.id = :id")
    Optional<Alert> findByIdForUpdate(
            @Param("id") Long id);

    /**
     * Finds distinct recipients with source-derived unresolved alerts so automation can also resolve conditions that disappeared.
     */
    @Query("""
            SELECT DISTINCT alert.employee.id FROM Alert alert
            WHERE alert.type = :type AND alert.status IN :statuses AND alert.sourceKey IS NOT NULL
            """)
    List<Long> findUnresolvedRecipientIds(
            @Param("type") Alert.Type type,
                                        @Param("statuses") Collection<Alert.Status> statuses);

    /**
     * Locks a recipient's unresolved source alerts of one type in ID order for reconciliation and duplicate handling.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT alert FROM Alert alert
            WHERE alert.employee.id = :employeeId AND alert.type = :type
              AND alert.status IN :statuses AND alert.sourceKey IS NOT NULL
            ORDER BY alert.id
            """)
    List<Alert> findUnresolvedForUpdate(
            @Param("employeeId") Long employeeId,
                                       @Param("type") Alert.Type type,
                                       @Param("statuses") Collection<Alert.Status> statuses);
}
