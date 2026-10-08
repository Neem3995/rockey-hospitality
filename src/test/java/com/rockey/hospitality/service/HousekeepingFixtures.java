package com.rockey.hospitality.service;
import com.rockey.hospitality.entity.*;
import org.springframework.test.util.ReflectionTestUtils;
final class HousekeepingFixtures {
    private HousekeepingFixtures() { }
    static User user(long id, User.Role role) {
        User user = new User("Test worker", "worker" + id + "@example.test", "test-only-hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
    static Room room(long id, Room.Status status) {
        Room room = new Room("R" + id, 1, status); ReflectionTestUtils.setField(room, "id", id); return room;
    }
    static Task task(long id, User user, Room room) {
        Task task = new Task("Clean room", "Test work", Task.Priority.MEDIUM, user, room, null);
        ReflectionTestUtils.setField(task, "id", id); return task;
    }
}
