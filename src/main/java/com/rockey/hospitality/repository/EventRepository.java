package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data JPA supplies standard persistence operations for Event entities through JpaRepository.
 * Domain services use the methods below for filtered reads, eligibility checks, and locked writes where declared.
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Pages Events by optional status and inclusive eventDateTime bounds using named parameters.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT event
            FROM Event event
            WHERE (:status IS NULL OR event.status = :status)
              AND (:dateFrom IS NULL OR event.eventDateTime >= :dateFrom)
              AND (:dateTo IS NULL OR event.eventDateTime <= :dateTo)
            """)
    Page<Event> search(
            // Binds this argument as the named status query parameter, not interpolated query text.
            @Param("status") EventStatus status,
            // Binds this argument as the named dateFrom query parameter, not interpolated query text.
            @Param("dateFrom") LocalDateTime dateFrom,
            // Binds this argument as the named dateTo query parameter, not interpolated query text.
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    /**
     * Locks one Event so capacity checks, registration, and lifecycle writes serialize.
     */
    // Acquires a PESSIMISTIC_WRITE database row lock until the caller's transaction ends.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("SELECT event FROM Event event WHERE event.id = :eventId")
    Optional<Event> findByIdForUpdate(
            // Binds this argument as the named eventId query parameter, not interpolated query text.
            @Param("eventId") Long eventId);

    /**
     * Counts retained User memberships for one Event through the join table.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT COUNT(registeredEvent)
            FROM User user
            JOIN user.registeredEvents registeredEvent
            WHERE registeredEvent.id = :eventId
            """)
    long countRegistrationsByEventId(
            // Binds this argument as the named eventId query parameter, not interpolated query text.
            @Param("eventId") Long eventId);

    /**
     * Pages only Events present in one User's registration memberships.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT event FROM Event event
            WHERE event.id IN (
                SELECT registeredEvent.id FROM User user
                JOIN user.registeredEvents registeredEvent
                WHERE user.id = :userId
            )
            """)
    Page<Event> findRegisteredEventsByUserId(
            // Binds this argument as the named userId query parameter, not interpolated query text.
            @Param("userId") Long userId,
            Pageable pageable
    );
}
