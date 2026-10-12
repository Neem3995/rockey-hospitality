package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.PageResponse;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.rockey.hospitality.service.HousekeepingFixtures.*;

class PaginationTest {
    @Test void userTaskPageAndTotalShareOwnerAndStatusScope() {
        TaskRepository rows = mock(TaskRepository.class); UserService users = mock(UserService.class);
        when(users.account(2L)).thenReturn(user(2,User.Role.USER));
        var request=PageRequest.of(0,1,Sort.by("id").descending());
        when(rows.findScoped(2L,Task.Status.ASSIGNED,request)).thenReturn(new PageImpl<>(List.of(task(8,user(2,User.Role.USER),room(1,Room.Status.DIRTY))),request,3));
        var service=new TaskService(rows,mock(RoomRepository.class),mock(UserRepository.class),users,Clock.systemUTC());
        var result=service.page(2L,0,1,Task.Status.ASSIGNED);
        assertEquals(3,result.totalElements()); assertEquals(3,result.totalPages()); assertEquals(8,result.items().get(0).getId());
        verify(rows).findScoped(2L,Task.Status.ASSIGNED,request);
        verify(rows,never()).findAll(any(Pageable.class));
    }
    @Test void managerTaskPageUsesSupervisorScopeAndInactiveCallerIsDenied() {
        TaskRepository rows=mock(TaskRepository.class); UserService users=mock(UserService.class);
        User manager=user(3,User.Role.MANAGER); when(users.account(3L)).thenReturn(manager);
        var request=PageRequest.of(0,20,Sort.by("id").descending());
        when(rows.findScoped(null,null,request)).thenReturn(Page.empty(request));
        var service=new TaskService(rows,mock(RoomRepository.class),mock(UserRepository.class),users,Clock.systemUTC());
        assertEquals(0,service.page(3L,0,20,null).totalElements());
        manager.deactivate(); assertThrows(ForbiddenException.class,()->service.page(3L,0,20,null));
    }
    @Test void managerAccountPageCannotCountPrivilegedAccountsAndHasStableNameTieBreak() {
        UserRepository rows=mock(UserRepository.class);
        when(rows.findById(3L)).thenReturn(Optional.of(user(3,User.Role.MANAGER)));
        var request=PageRequest.of(0,20,Sort.by("name").ascending().and(Sort.by("id")));
        when(rows.findByRole(User.Role.USER,request)).thenReturn(new PageImpl<>(List.of(user(2,User.Role.USER)),request,1));
        var service=new UserService(rows,mock(TaskRepository.class),mock(AuthService.class));
        assertEquals(1,service.page(3L,0,20).totalElements());
        verify(rows).findByRole(User.Role.USER,request); verify(rows,never()).findAll(any(Pageable.class));
        when(rows.findById(2L)).thenReturn(Optional.of(user(2,User.Role.USER)));
        assertThrows(ForbiddenException.class,()->service.page(2L,0,20));
    }
    @Test void adminAccountPageIncludesAllAccounts() {
        UserRepository rows=mock(UserRepository.class); when(rows.findById(1L)).thenReturn(Optional.of(user(1,User.Role.ADMIN)));
        when(rows.findAll(any(Pageable.class))).thenAnswer(call->new PageImpl<>(List.of(user(1,User.Role.ADMIN)),call.getArgument(0),1));
        assertEquals(User.Role.ADMIN,new UserService(rows,mock(TaskRepository.class),mock(AuthService.class)).page(1L,0,20).items().get(0).getRole());
    }
    @Test void emptyAndOutOfRangePagesPreserveAuthorizedTotals() {
        var request=PageRequest.of(9,20);
        var result=PageResponse.from(new PageImpl<String>(List.of(),request,21));
        assertEquals(21,result.totalElements()); assertEquals(2,result.totalPages()); assertTrue(result.items().isEmpty()); assertEquals(9,result.page());
        assertEquals(0,PageResponse.from(Page.<String>empty(PageRequest.of(0,20))).totalPages());
    }
    @Test void invalidBoundsCannotRequestUnboundedRows() {
        for(int[] bounds:List.of(new int[]{-1,20},new int[]{0,0},new int[]{0,101},new int[]{1000001,20}))
            assertThrows(BadRequestException.class,()->PageResponse.request(bounds[0],bounds[1],Sort.by("id")));
    }
    @Test void roomsAndHistoryUseStableSortAndExistingSupervisorGate() {
        RoomRepository rooms=mock(RoomRepository.class); InspectionRepository inspections=mock(InspectionRepository.class); UserService users=mock(UserService.class);
        when(rooms.findById(4L)).thenReturn(Optional.of(room(4,Room.Status.READY)));
        when(rooms.findAll(any(Pageable.class))).thenAnswer(call->Page.empty(call.getArgument(0)));
        when(inspections.findByRoomId(eq(4L),any(Pageable.class))).thenAnswer(call->Page.empty(call.getArgument(1)));
        var service=new RoomService(rooms,mock(TaskRepository.class),inspections,users,Clock.systemUTC());
        assertTrue(service.page(1L,0,20).items().isEmpty()); assertTrue(service.inspectionPage(1L,4L,0,20).items().isEmpty());
        verify(rooms).findAll(PageRequest.of(0,20,Sort.by("roomNumber").ascending().and(Sort.by("id"))));
        verify(inspections).findByRoomId(4L,PageRequest.of(0,20,Sort.by("id").descending()));
        verify(users,times(2)).requireSupervisor(1L);
        doThrow(new ForbiddenException("Denied")).when(users).requireSupervisor(2L);
        assertThrows(ForbiddenException.class,()->service.inspectionPage(2L,4L,0,20));
    }
}
