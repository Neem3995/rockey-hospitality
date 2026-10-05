package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;
import com.rockey.hospitality.dto.task.TaskSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            SELECT task FROM Task task
            WHERE task.assignedEmployee IS NOT NULL AND task.status NOT IN :terminalStatuses
              AND (task.dueAt < :now OR task.priority IN :priorities)
            """)
    List<Task> findTaskAlertSources(@Param("now") LocalDateTime now,
                                    @Param("terminalStatuses") Collection<TaskStatus> terminalStatuses,
                                    @Param("priorities") Collection<TaskPriority> priorities);

    @Query("""
            SELECT task
            FROM Task task
            WHERE (:#{#criteria.departmentId} IS NULL OR task.department.id = :#{#criteria.departmentId})
              AND (:#{#criteria.status} IS NULL OR task.status = :#{#criteria.status})
              AND (:#{#criteria.priority} IS NULL OR task.priority = :#{#criteria.priority})
              AND (:#{#criteria.assignedEmployeeId} IS NULL
                   OR task.assignedEmployee.id = :#{#criteria.assignedEmployeeId})
              AND (:#{#criteria.roomId} IS NULL OR task.room.id = :#{#criteria.roomId})
              AND (:#{#criteria.eventId} IS NULL OR task.event.id = :#{#criteria.eventId})
              AND (
                    :#{#criteria.overdue} IS NULL
                    OR (
                        :#{#criteria.overdue} = TRUE
                        AND task.dueAt IS NOT NULL
                        AND task.dueAt < :now
                        AND task.status NOT IN :terminalStatuses
                    )
                    OR (
                        :#{#criteria.overdue} = FALSE
                        AND (
                            task.dueAt IS NULL
                            OR task.dueAt >= :now
                            OR task.status IN :terminalStatuses
                        )
                    )
              )
            """)
    Page<Task> search(
            @Param("criteria") TaskSearchCriteria criteria,
            @Param("now") LocalDateTime now,
            @Param("terminalStatuses") Collection<TaskStatus> terminalStatuses,
            Pageable pageable
    );

    boolean existsByAssignedEmployeeIdAndStatusIn(
            Long employeeId,
            Collection<TaskStatus> statuses
    );

    boolean existsByDepartmentIdAndStatusIn(
            Long departmentId,
            Collection<TaskStatus> statuses
    );

    boolean existsByRoomIdAndStatusIn(
            Long roomId,
            Collection<TaskStatus> statuses
    );

    long countByEventId(Long eventId);

    long countByEventIdAndStatus(Long eventId, TaskStatus status);
}
