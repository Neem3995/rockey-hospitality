package com.rockey.hospitality.service;
import com.rockey.hospitality.dto.RoomDtos.*;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {
    @Mock RoomRepository rooms; @Mock TaskRepository tasks; @Mock InspectionRepository inspections; @Mock UserService users;
    RoomService service; Room room; Task task; User manager;
    @BeforeEach void setup() {
        manager=HousekeepingFixtures.user(2, User.Role.MANAGER); room=HousekeepingFixtures.room(1, Room.Status.READY);
        task=HousekeepingFixtures.task(1, HousekeepingFixtures.user(3, User.Role.USER), room);
        service=new RoomService(rooms,tasks,inspections,users,Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"),ZoneOffset.UTC));
        lenient().when(users.requireSupervisor(2L)).thenReturn(manager);
        lenient().when(rooms.findById(1L)).thenReturn(Optional.of(room)); lenient().when(rooms.findForUpdate(1L)).thenReturn(Optional.of(room));
        lenient().when(rooms.save(any())).thenAnswer(i->i.getArgument(0));
        lenient().when(inspections.save(any())).thenAnswer(i->i.getArgument(0));
    }
    RoomRequest request() { return new RoomRequest("R1", 2, null, true); }
    @Test void listRooms() { when(rooms.findAllByOrderByRoomNumberAsc()).thenReturn(List.of(room)); assertEquals(1,service.list(2L).size()); }
    @Test void getRoom() { assertEquals("R1",service.get(2L,1L).getRoomNumber()); }
    @Test void missingRoom404() { assertThrows(ResourceNotFoundException.class,()->service.get(2L,99L)); }
    @Test void createDefaultReady() { var response=service.create(2L,request()); assertEquals(Room.Status.READY,response.getStatus()); }
    @Test void createInactive() { var request=new RoomRequest("New",1,Room.Status.DIRTY,false); assertFalse(service.create(2L,request).getActive()); }
    @Test void duplicate409() { when(rooms.existsByRoomNumberIgnoreCase("R1")).thenReturn(true); var request=request(); assertThrows(ConflictException.class,()->service.create(2L,request)); }
    @Test void cannotInventInspectionState() { var request=new RoomRequest("New",1,Room.Status.INSPECTION,true); assertThrows(ConflictException.class,()->service.create(2L,request)); }
    @Test void updateFloor() { assertEquals(2,service.update(2L,1L,request()).getFloor()); }
    @Test void cannotRenumber() { var request=new RoomRequest("Other",1,null,true); assertThrows(ConflictException.class,()->service.update(2L,1L,request)); }
    @Test void lifecycleMustUseStatusOperation() { var request=new RoomRequest("R1",1,Room.Status.DIRTY,true); assertThrows(ConflictException.class,()->service.update(2L,1L,request)); }
    @Test void markDirty() { assertEquals(Room.Status.DIRTY,service.status(2L,1L,Room.Status.DIRTY).getStatus()); }
    @Test void takeOutOfServiceAndReturnDirty() { service.status(2L,1L,Room.Status.OUT_OF_SERVICE); assertEquals(Room.Status.DIRTY,service.status(2L,1L,Room.Status.DIRTY).getStatus()); }
    @Test void dirtyCanBeOutOfService() { room.updateStatus(Room.Status.DIRTY); assertEquals(Room.Status.OUT_OF_SERVICE,service.status(2L,1L,Room.Status.OUT_OF_SERVICE).getStatus()); }
    @Test void sameStatusSafe() { assertEquals(Room.Status.READY,service.status(2L,1L,Room.Status.READY).getStatus()); }
    @Test void cannotBypassInspection() { room.updateStatus(Room.Status.INSPECTION); assertThrows(ConflictException.class,()->service.status(2L,1L,Room.Status.READY)); }
    @Test void cannotSkipCleaning() { assertThrows(ConflictException.class,()->service.status(2L,1L,Room.Status.CLEANING)); }
    @Test void inactiveStatus409() { room.deactivate(); assertThrows(ConflictException.class,()->service.status(2L,1L,Room.Status.DIRTY)); }
    @Test void activeWorkBlocksDeactivation() {
        when(tasks.findByRoomIdAndStatusIn(eq(1L),any())).thenReturn(List.of(task));
        assertThrows(ConflictException.class,()->service.deactivate(2L,1L));
        var request=new RoomRequest("R1",1,null,false);
        assertThrows(ConflictException.class,()->service.update(2L,1L,request));
    }
    @Test void deactivationPreservesRow() { service.deactivate(2L,1L); assertFalse(room.isActive()); verify(rooms).save(room); }
    void inspectionReady() {
        room.updateStatus(Room.Status.INSPECTION); task.complete(LocalDateTime.now());
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        when(tasks.findFirstByRoomIdAndStatusOrderByIdDesc(1L,Task.Status.COMPLETED)).thenReturn(Optional.of(task));
    }
    @Test void passRecordsInspectorAndReadiness() {
        inspectionReady(); var result=service.inspect(2L,1L,new InspectionRequest(1L,Inspection.Result.PASS,"Checked"));
        assertEquals(Room.Status.READY,room.getStatus()); assertEquals(2L,result.getInspectedBy().getId()); assertNotNull(result.getInspectedAt());
    }
    @Test void failReturnsRoomDirty() { inspectionReady(); service.inspect(2L,1L,new InspectionRequest(1L,Inspection.Result.FAIL,"Redo")); assertEquals(Room.Status.DIRTY,room.getStatus()); }
    @Test void inspectionWrongTask409() {
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        var request=new InspectionRequest(1L,Inspection.Result.PASS,"");
        assertThrows(ConflictException.class,()->service.inspect(2L,1L,request));
    }
    @Test void inspectionTask404() { var request=new InspectionRequest(99L,Inspection.Result.PASS,""); assertThrows(ResourceNotFoundException.class,()->service.inspect(2L,1L,request)); }
    @Test void oldCompletedTaskRejected() {
        inspectionReady(); when(tasks.findFirstByRoomIdAndStatusOrderByIdDesc(1L,Task.Status.COMPLETED)).thenReturn(Optional.of(HousekeepingFixtures.task(2,manager,room)));
        var request=new InspectionRequest(1L,Inspection.Result.PASS,""); assertThrows(ConflictException.class,()->service.inspect(2L,1L,request));
    }
    @Test void historySafe() {
        Inspection record=new Inspection(room,task,manager,Inspection.Result.PASS,"Fine",LocalDateTime.now());
        when(inspections.findByRoomIdOrderByIdDesc(1L)).thenReturn(List.of(record)); assertEquals(1,service.inspectionHistory(2L,1L).size());
    }
    @Test void writeFailurePropagatesForTransactionRollback() {
        inspectionReady(); when(inspections.save(any())).thenThrow(new IllegalStateException("test failure"));
        var request=new InspectionRequest(1L,Inspection.Result.PASS,"");
        assertThrows(IllegalStateException.class,()->service.inspect(2L,1L,request)); assertEquals(Room.Status.INSPECTION,room.getStatus());
    }
}
