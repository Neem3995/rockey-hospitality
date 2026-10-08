package com.rockey.hospitality.service;
import com.rockey.hospitality.dto.TaskDtos.*;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock TaskRepository tasks; @Mock RoomRepository rooms; @Mock UserRepository accounts; @Mock UserService users;
    TaskService service; User worker, manager; Room room; Task task;
    @BeforeEach void setup() {
        worker = HousekeepingFixtures.user(3, User.Role.USER); manager = HousekeepingFixtures.user(2, User.Role.MANAGER);
        room = HousekeepingFixtures.room(1, Room.Status.DIRTY); task = HousekeepingFixtures.task(1, worker, room);
        service = new TaskService(tasks, rooms, accounts, users, Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC));
        lenient().when(users.account(3L)).thenReturn(worker); lenient().when(users.account(2L)).thenReturn(manager);
        lenient().when(accounts.findForUpdate(3L)).thenReturn(Optional.of(worker));
        lenient().when(tasks.findById(1L)).thenReturn(Optional.of(task)); lenient().when(tasks.findForUpdate(1L)).thenReturn(Optional.of(task));
        lenient().when(tasks.findRoomId(1L)).thenReturn(Optional.of(1L)); lenient().when(rooms.findForUpdate(1L)).thenReturn(Optional.of(room));
        lenient().when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    TaskRequest request() { return new TaskRequest("Clean room", "Test work", Task.Priority.HIGH, 3L, 1L, null); }
    @Test void workerListIsScopedAtRepository() { when(tasks.findByAssignedUserIdOrderByIdDesc(3L)).thenReturn(List.of(task)); assertEquals(1, service.list(3L).size()); verify(tasks, never()).findAllByOrderByIdDesc(); }
    @Test void managerListGlobal() { when(tasks.findAllByOrderByIdDesc()).thenReturn(List.of(task)); assertEquals(1, service.list(2L).size()); }
    @Test void ownTaskReadable() { assertEquals("R1", service.get(3L, 1L).getRoom().getRoomNumber()); }
    @Test void crossUserTaskDenied() {
        when(users.account(4L)).thenReturn(HousekeepingFixtures.user(4, User.Role.USER));
        assertThrows(ForbiddenException.class, () -> service.get(4L, 1L));
        assertThrows(ForbiddenException.class, () -> service.status(4L, 1L, Task.Status.IN_PROGRESS));
        task.start(); room.updateStatus(Room.Status.CLEANING);
        assertThrows(ForbiddenException.class, () -> service.status(4L, 1L, Task.Status.COMPLETED));
        assertEquals(Task.Status.IN_PROGRESS, task.getStatus());
        verify(tasks, never()).save(any()); verify(rooms, never()).save(any());
    }
    @Test void inactiveCallerDenied() { worker.deactivate(); assertThrows(ForbiddenException.class, () -> service.list(3L)); }
    @Test void missingDetail404() { assertThrows(ResourceNotFoundException.class, () -> service.get(3L, 99L)); }
    @Test void createAssignedWork() { assertEquals(Task.Status.ASSIGNED, service.create(2L, request()).getStatus()); }
    @Test void inactiveAssignee409() { worker.deactivate(); var request=request(); assertThrows(ConflictException.class, () -> service.create(2L, request)); }
    @Test void supervisorCannotBeAssignee() { when(accounts.findForUpdate(3L)).thenReturn(Optional.of(manager)); var request=request(); assertThrows(ConflictException.class, () -> service.create(2L, request)); }
    @Test void absentAssignee404() { var request=new TaskRequest("Clean room", "", Task.Priority.LOW, 99L, 1L, null); assertThrows(ResourceNotFoundException.class, () -> service.create(2L, request)); }
    @Test void roomMustBeDirty() { room.updateStatus(Room.Status.READY); var request=request(); assertThrows(ConflictException.class, () -> service.create(2L, request)); }
    @Test void inactiveRoom409() { room.deactivate(); var request=request(); assertThrows(ConflictException.class, () -> service.create(2L, request)); }
    @Test void duplicateActiveWork409() { when(tasks.findByRoomIdAndStatusIn(eq(1L), any())).thenReturn(List.of(task)); var request=request(); assertThrows(ConflictException.class, () -> service.create(2L, request)); }
    @Test void absentRoom404() { var request=new TaskRequest("Clean room", "", Task.Priority.LOW, 3L, 99L, null); assertThrows(ResourceNotFoundException.class, () -> service.create(2L, request)); }
    @Test void updateWork() { assertEquals(Task.Priority.HIGH, service.update(2L, 1L, request()).getPriority()); }
    @Test void cannotChangeTaskRoom() { var request=new TaskRequest("Clean room", "", Task.Priority.LOW, 3L, 99L, null); assertThrows(ConflictException.class, () -> service.update(2L, 1L, request)); }
    @Test void terminalWorkNotEditable() { task.cancel(); var request=request(); assertThrows(ConflictException.class, () -> service.update(2L, 1L, request)); }
    @Test void startUpdatesRoomAtomically() { assertEquals(Task.Status.IN_PROGRESS, service.status(3L, 1L, Task.Status.IN_PROGRESS).getStatus()); assertEquals(Room.Status.CLEANING, room.getStatus()); }
    @Test void completeUpdatesInspectionStateAndTimestamp() { task.start(); room.updateStatus(Room.Status.CLEANING); var response=service.status(3L, 1L, Task.Status.COMPLETED); assertNotNull(response.getCompletedAt()); assertEquals(Room.Status.INSPECTION, room.getStatus()); }
    @ParameterizedTest
    @EnumSource(value = User.Role.class, names = {"MANAGER", "ADMIN"})
    void supervisorsCannotExecuteHousekeeperWork(User.Role role) {
        when(users.account(2L)).thenReturn(HousekeepingFixtures.user(2, role));
        assertThrows(ForbiddenException.class, () -> service.status(2L, 1L, Task.Status.IN_PROGRESS));
        assertEquals(Task.Status.ASSIGNED, task.getStatus()); assertEquals(Room.Status.DIRTY, room.getStatus());
        task.start(); room.updateStatus(Room.Status.CLEANING);
        assertThrows(ForbiddenException.class, () -> service.status(2L, 1L, Task.Status.COMPLETED));
        assertEquals(Task.Status.IN_PROGRESS, task.getStatus()); assertEquals(Room.Status.CLEANING, room.getStatus());
        verify(tasks, never()).save(any()); verify(rooms, never()).save(any());
    }
    @Test void cannotCompleteBeforeStart() { assertThrows(ConflictException.class, () -> service.status(3L, 1L, Task.Status.COMPLETED)); }
    @Test void workerCannotCancel() { assertThrows(ConflictException.class, () -> service.status(3L, 1L, Task.Status.CANCELLED)); }
    @Test void managerStatusCanCancel() { assertEquals(Task.Status.CANCELLED, service.status(2L, 1L, Task.Status.CANCELLED).getStatus()); }
    @Test void cancelInProgressReturnsRoomToDirty() { task.start(); room.updateStatus(Room.Status.CLEANING); service.cancel(2L, 1L); assertEquals(Room.Status.DIRTY, room.getStatus()); assertEquals(Task.Status.CANCELLED, task.getStatus()); }
    @Test void repeatCancelSafe() { task.cancel(); service.cancel(2L, 1L); verify(tasks, never()).save(any()); }
    @Test void cannotCancelCompletedHistory() { task.complete(LocalDateTime.now()); assertThrows(ConflictException.class, () -> service.cancel(2L, 1L)); }
    @Test void disabledAssigneeCannotStart() { worker.deactivate(); assertThrows(ForbiddenException.class, () -> service.status(3L, 1L, Task.Status.IN_PROGRESS)); }
    @Test void missingStatusTask404() { assertThrows(ResourceNotFoundException.class, () -> service.status(3L, 99L, Task.Status.IN_PROGRESS)); }
}
