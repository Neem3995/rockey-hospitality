package com.rockey.hospitality.dto.alert;

public class AlertEmployeeSummary {

    private final Long id;
    private final String name;

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
