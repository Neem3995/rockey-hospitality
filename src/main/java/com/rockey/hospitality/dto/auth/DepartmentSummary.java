package com.rockey.hospitality.dto.auth;

public class DepartmentSummary {

    private final Long id;
    private final String name;

    public DepartmentSummary(Long id, String name) {
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
