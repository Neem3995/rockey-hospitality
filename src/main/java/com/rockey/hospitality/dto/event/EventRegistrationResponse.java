package com.rockey.hospitality.dto.event;

/**
 * Safe event registration response DTO built from validated service results instead of serializing the entity.
 */
public class EventRegistrationResponse {

    /**
     * User account identifier associated with this response or relationship.
     */
    private final Long userId;
    /**
     * Optional shallow Event context or registered Event summary.
     */
    private final EventSummary event;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public EventRegistrationResponse(Long userId, EventSummary event) {
        this.userId = userId;
        this.event = event;
    }

    public Long getUserId() {
        return userId;
    }

    public EventSummary getEvent() {
        return event;
    }
}
