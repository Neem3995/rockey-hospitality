package com.rockey.hospitality.dto.alert;

/**
 * Shallow alert task response DTO exposing only the listed fields, not a complete JPA relationship graph.
 */
public class AlertTaskSummary {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Human-readable work or Event title.
     */
    private final String title;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public AlertTaskSummary(
            Long id,
            String title
    ) {
        this.id = id;
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
