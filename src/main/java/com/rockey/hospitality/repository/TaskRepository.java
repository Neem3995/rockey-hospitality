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

/**
 * Spring Data JPA supplies standard persistence operations for Task entities through JpaRepository.
 * Domain services use the methods below for filtered reads, eligibility checks, and locked writes where declared.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Finds assigned non-terminal Tasks that are overdue or match selected priorities.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT task FROM Task task
            WHERE task.assignedEmployee IS NOT NULL AND task.status NOT IN :terminalStatuses
              AND (task.dueAt < :now OR task.priority IN :priorities)
            """)
    List<Task> findTaskAlertSources(
            // Binds this argument as the named now query parameter, not interpolated query text.
            @Param("now") LocalDateTime now,
                                    // Binds this argument as the named terminalStatuses query parameter, not interpolated query text.
                                    @Param("terminalStatuses") Collection<TaskStatus> terminalStatuses,
                                    // Binds this argument as the named priorities query parameter, not interpolated query text.
                                    @Param("priorities") Collection<TaskPriority> priorities);

    /**
     * Pages criteria with bound values and one supplied time snapshot.
     * overdue=false includes null/future due dates and terminal Tasks; an omitted overdue filter imposes no due condition.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
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
            // Criteria values bind as parameters; overdue uses the same server time for the page.
            // Binds this argument as the named criteria query parameter, not interpolated query text.
            @Param("criteria") TaskSearchCriteria criteria,
            // Binds this argument as the named now query parameter, not interpolated query text.
            @Param("now") LocalDateTime now,
            // Binds this argument as the named terminalStatuses query parameter, not interpolated query text.
            @Param("terminalStatuses") Collection<TaskStatus> terminalStatuses,
            Pageable pageable
    );

    /**
     * Checks active assignment statuses before Employee deactivation.
     */
    boolean existsByAssignedEmployeeIdAndStatusIn(
            Long employeeId,
            Collection<TaskStatus> statuses
    );

    /**
     * Checks non-terminal Department work before Department deactivation.
     */
    boolean existsByDepartmentIdAndStatusIn(
            Long departmentId,
            Collection<TaskStatus> statuses
    );

    /**
     * Checks active Room work before Room deactivation.
     */
    boolean existsByRoomIdAndStatusIn(
            Long roomId,
            Collection<TaskStatus> statuses
    );

    /**
     * Counts every retained preparation Task linked to an Event.
     */
    long countByEventId(Long eventId);

    /**
     * Counts Event-linked Tasks of a selected status, including completion counts.
     */
    long countByEventIdAndStatus(Long eventId, TaskStatus status);
}
