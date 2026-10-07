package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Event;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
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
 * EventService and RegistrationService use lifecycle search, membership counts and locks protecting Event
 * capacity.
 * Spring creates this interface's implementation and sends its queries through JPA/Hibernate to MySQL.
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    // Repository study key: findBy/existsBy/countBy names are interpreted by Spring Data as queries.
    // @Query supplies fixed JPQL (entity/field-based query text), not string-interpolated user input.
    // @Param binds a Java argument to a named query value instead of inserting it into query text.
    // @Lock(PESSIMISTIC_WRITE) keeps a database row locked until the caller's transaction ends to serialize
    // conflicting changes.

    /**
     * Pages Events by optional status and inclusive eventDateTime bounds using named parameters.
     */
    @Query("""
            SELECT event
            FROM Event event
            WHERE (:status IS NULL OR event.status = :status)
              AND (:dateFrom IS NULL OR event.eventDateTime >= :dateFrom)
              AND (:dateTo IS NULL OR event.eventDateTime <= :dateTo)
            """)
    Page<Event> search(
            @Param("status") Event.Status status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    /**
     * Locks one Event so capacity checks, registration, and lifecycle writes serialize.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT event FROM Event event WHERE event.id = :eventId")
    Optional<Event> findByIdForUpdate(
            @Param("eventId") Long eventId);

    /**
     * Counts retained User memberships for one Event through the join table.
     */
    @Query("""
            SELECT COUNT(registeredEvent)
            FROM User user
            JOIN user.registeredEvents registeredEvent
            WHERE registeredEvent.id = :eventId
            """)
    long countRegistrationsByEventId(
            @Param("eventId") Long eventId);

    /**
     * Pages only Events present in one User's registration memberships.
     */
    @Query("""
            SELECT event FROM Event event
            WHERE event.id IN (
                SELECT registeredEvent.id FROM User user
                JOIN user.registeredEvents registeredEvent
                WHERE user.id = :userId
            )
            """)
    Page<Event> findRegisteredEventsByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
