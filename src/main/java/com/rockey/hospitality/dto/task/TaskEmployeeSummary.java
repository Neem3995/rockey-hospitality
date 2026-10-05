package com.rockey.hospitality.dto.task;

public class TaskEmployeeSummary {

    private final Long id;
    private final String name;

    public TaskEmployeeSummary(Long id, String name) {
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
