package com.rockey.hospitality.dto.event;

public class EventRegistrationResponse {

    private final Long userId;
    private final EventSummary event;

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
