package com.rockey.hospitality.dto.alert;

public class AlertTaskSummary {

    private final Long id;
    private final String title;

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
