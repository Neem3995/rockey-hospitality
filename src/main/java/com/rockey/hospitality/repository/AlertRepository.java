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

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("""
            SELECT alert FROM Alert alert
            WHERE (:employeeId IS NULL OR alert.employee.id = :employeeId)
              AND (:type IS NULL OR alert.type = :type)
              AND ((:status IS NULL AND alert.status <> :resolved) OR alert.status = :status)
            """)
    Page<Alert> search(@Param("employeeId") Long employeeId, @Param("type") AlertType type,
                       @Param("status") AlertStatus status, @Param("resolved") AlertStatus resolved,
                       Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT alert FROM Alert alert WHERE alert.id = :id")
    Optional<Alert> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT alert.employee.id FROM Alert alert
            WHERE alert.type = :type AND alert.status IN :statuses AND alert.sourceKey IS NOT NULL
            """)
    List<Long> findUnresolvedRecipientIds(@Param("type") AlertType type,
                                        @Param("statuses") Collection<AlertStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT alert FROM Alert alert
            WHERE alert.employee.id = :employeeId AND alert.type = :type
              AND alert.status IN :statuses AND alert.sourceKey IS NOT NULL
            ORDER BY alert.id
            """)
    List<Alert> findUnresolvedForUpdate(@Param("employeeId") Long employeeId,
                                       @Param("type") AlertType type,
                                       @Param("statuses") Collection<AlertStatus> statuses);
}
