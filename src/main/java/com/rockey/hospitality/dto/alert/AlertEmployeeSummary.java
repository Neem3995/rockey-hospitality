package com.rockey.hospitality.dto.alert;

/**
 * Shallow alert employee response DTO exposing only the listed fields, not a complete JPA relationship graph.
 */
public class AlertEmployeeSummary {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Display name used by this resource's request or response, not an authorization role.
     */
    private final String name;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public AlertEmployeeSummary(
            Long id,
            String name
    ) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
