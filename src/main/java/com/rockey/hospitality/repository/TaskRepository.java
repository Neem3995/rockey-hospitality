package com.rockey.hospitality.repository;

import com.rockey.hospitality.dto.TaskDtos.TaskSearchCriteria;
import com.rockey.hospitality.entity.Task;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * STUDY NOTE: A Repository is the data-access layer a Service uses to reach database data.
 * JpaRepository lets Spring Data supply standard create/read/update/delete methods without writing basic
 * SQL.
 * TaskService and related services use filtered pages, assigned-work guards and Event preparation counts.
 * Spring creates this interface's implementation and sends its queries through JPA/Hibernate to MySQL.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    // Repository study key: findBy/existsBy/countBy names are interpreted by Spring Data as queries.
    // @Query supplies fixed JPQL (entity/field-based query text), not string-interpolated user input.
    // @Param binds a Java argument to a named query value instead of inserting it into query text.

    /**
     * Finds assigned non-terminal Tasks that are overdue or match selected priorities.
     */
    @Query("""
            SELECT task FROM Task task
            WHERE task.assignedEmployee IS NOT NULL AND task.status NOT IN :terminalStatuses
              AND (task.dueAt < :now OR task.priority IN :priorities)
            """)
    List<Task> findTaskAlertSources(
            @Param("now") LocalDateTime now,
                                    @Param("terminalStatuses") Collection<Task.Status> terminalStatuses,
                                    @Param("priorities") Collection<Task.Priority> priorities);

    /**
     * Pages criteria with bound values and one supplied time snapshot.
     * overdue=false includes null/future due dates and terminal Tasks; an omitted overdue filter imposes no due condition.
     */
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
            @Param("criteria") TaskSearchCriteria criteria,
            @Param("now") LocalDateTime now,
            @Param("terminalStatuses") Collection<Task.Status> terminalStatuses,
            Pageable pageable
    );

    /**
     * Checks active assignment statuses before Employee deactivation.
     */
    boolean existsByAssignedEmployeeIdAndStatusIn(
            Long employeeId,
            Collection<Task.Status> statuses
    );

    /**
     * Checks non-terminal Department work before Department deactivation.
     */
    boolean existsByDepartmentIdAndStatusIn(
            Long departmentId,
            Collection<Task.Status> statuses
    );

    /**
     * Checks active Room work before Room deactivation.
     */
    boolean existsByRoomIdAndStatusIn(
            Long roomId,
            Collection<Task.Status> statuses
    );

    /**
     * Counts every retained preparation Task linked to an Event.
     */
    long countByEventId(Long eventId);

    /**
     * Counts Event-linked Tasks of a selected status, including completion counts.
     */
    long countByEventIdAndStatus(Long eventId, Task.Status status);
}
