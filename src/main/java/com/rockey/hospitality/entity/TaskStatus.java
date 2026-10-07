package com.rockey.hospitality.entity;

/**
 * Tracks OPEN, ASSIGNED, and IN_PROGRESS work, with COMPLETED and CANCELLED as terminal history.
 * TaskService enforces transitions and assignee requirements.
 */
public enum TaskStatus {
    /**
     * Unassigned, non-terminal work.
     */
    OPEN,
    /**
     * Non-terminal work with an assignee.
     */
    ASSIGNED,
    /**
     * Assigned work currently underway.
     */
    IN_PROGRESS,
    /**
     * Terminal work with completion time and retained assignee history.
     */
    COMPLETED,
    /**
     * Terminal cancelled work retaining references without a completion time.
     */
    CANCELLED
}
