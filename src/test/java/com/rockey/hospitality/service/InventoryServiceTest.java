package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.inventory.CreateInventoryItemRequest;
import com.rockey.hospitality.dto.inventory.InventoryItemResponse;
import com.rockey.hospitality.dto.inventory.UpdateInventoryItemRequest;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.InventoryItem;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryItemRepository inventoryRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    private InventoryService service;
    private Department housekeeping;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, departmentRepository, employeeRepository);
        housekeeping = department(3L, true);
    }

    @Test
    void createNormalizesNameAndSkuAndPersistsThresholdAndDepartment() {
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(housekeeping));
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> {
            InventoryItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 88L);
            return item;
        });
        CreateInventoryItemRequest request = createRequest();
        request.setName("  Bath Towels  ");
        request.setSku("  hk-towel-bath  ");

        InventoryItemResponse result = service.createInventoryItem(request);

        assertThat(result.getId()).isEqualTo(88L);
        assertThat(result.getName()).isEqualTo("Bath Towels");
        assertThat(result.getSku()).isEqualTo("HK-TOWEL-BATH");
        assertThat(result.getQuantity()).isEqualTo(18);
        assertThat(result.getReorderThreshold()).isEqualTo(20);
        assertThat(result.getDepartment().getId()).isEqualTo(3L);
        assertThat(result.getActive()).isTrue();
        verify(inventoryRepository).existsBySkuIgnoreCase("HK-TOWEL-BATH");
    }

    @Test
    void omittedCreateCountsDefaultToZero() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest();
        request.setName("Bath Towels");
        request.setSku("HK-TOWEL-BATH");
        request.setDepartmentId(3L);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(housekeeping));
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(i -> i.getArgument(0));

        InventoryItemResponse result = service.createInventoryItem(request);

        assertThat(result.getQuantity()).isZero();
        assertThat(result.getReorderThreshold()).isZero();
    }

    @Test
    void duplicateSkuRemainsUnavailableIncludingInactiveItems() {
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(housekeeping));
        when(inventoryRepository.existsBySkuIgnoreCase("HK-TOWEL-BATH")).thenReturn(true);

        CreateInventoryItemRequest request = createRequest();
        assertThatThrownBy(() -> service.createInventoryItem(request))
                .isInstanceOf(ConflictException.class);
        verify(inventoryRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad_sku", "bad sku", "!", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void invalidSkuIsRejected(String sku) {
        CreateInventoryItemRequest request = createRequest();
        request.setSku(sku);
        assertThatThrownBy(() -> service.createInventoryItem(request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(inventoryRepository, departmentRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " X "})
    void trimmedNameMustHaveAtLeastTwoCharacters(String name) {
        CreateInventoryItemRequest request = createRequest();
        request.setName(name);
        assertThatThrownBy(() -> service.createInventoryItem(request))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"-1,0", "0,-1", "null,0", "0,null"}, nullValues = "null")
    void createRejectsNegativeOrNullCounts(Integer quantity, Integer threshold) {
        CreateInventoryItemRequest request = createRequest();
        request.setQuantity(quantity);
        request.setReorderThreshold(threshold);
        assertThatThrownBy(() -> service.createInventoryItem(request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(inventoryRepository, departmentRepository);
    }

    @Test
    void createRejectsInactiveDepartment() {
        housekeeping.deactivate();
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(housekeeping));
        CreateInventoryItemRequest request = createRequest();
        assertThatThrownBy(() -> service.createInventoryItem(request))
                .isInstanceOf(ConflictException.class);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingDepartment() {
        CreateInventoryItemRequest request = createRequest();
        assertThatThrownBy(() -> service.createInventoryItem(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidDepartmentId() {
        CreateInventoryItemRequest request = createRequest();
        request.setDepartmentId(0L);
        assertThatThrownBy(() -> service.createInventoryItem(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminListPassesDepartmentActivePaginationAndSortFilters() {
        when(inventoryRepository.search(eq(3L), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item()), PageRequest.of(1, 5), 6));

        var result = service.listInventory(3L, false, 1, 5, "quantity,desc", 1L, Role.ADMIN);

        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryRepository).search(eq(3L), eq(false), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getValue().getPageSize()).isEqualTo(5);
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "quantity"));
    }

    @Test
    void adminDefaultListIncludesAllDepartmentsAndLifecycleStates() {
        when(inventoryRepository.search(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var result = service.listInventory(null, null, 0, 20, null, 1L, Role.ADMIN);
        assertThat(result.getContent()).isEmpty();
        verify(inventoryRepository).search(null, null, PageRequest.of(0, 20, Sort.by("name")));
    }

    @Test
    void staffListAlwaysScopesToOwnActiveDepartment() {
        staff(EmployeeStatus.ACTIVE);
        when(inventoryRepository.search(eq(3L), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item())));
        var result = service.listInventory(null, null, 0, 20, "name,asc", 21L, Role.STAFF);
        assertThat(result.getContent()).hasSize(1);
        verify(inventoryRepository).search(eq(3L), eq(true), any(Pageable.class));
    }

    @Test
    void staffExplicitOwnDepartmentFilterIsAllowed() {
        staff(EmployeeStatus.ACTIVE);
        when(inventoryRepository.search(eq(3L), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        service.listInventory(3L, true, 0, 20, "sku", 21L, Role.STAFF);
        verify(inventoryRepository).search(eq(3L), eq(true), any(Pageable.class));
    }

    @Test
    void staffCannotFilterAnotherDepartment() {
        staff(EmployeeStatus.ACTIVE);
        assertThatThrownBy(() -> service.listInventory(4L, null, 0, 20, null, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void staffCannotRequestInactiveInventory() {
        staff(EmployeeStatus.ACTIVE);
        assertThatThrownBy(() -> service.listInventory(null, false, 0, 20, null, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void staffWithoutEmployeeCannotList() {
        assertThatThrownBy(() -> service.listInventory(null, null, 0, 20, null, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inactiveEmployeeCannotReadInventory() {
        staff(EmployeeStatus.INACTIVE);
        assertThatThrownBy(() -> service.getInventoryItem(88L, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void inactiveStaffDepartmentCannotListInventory() {
        housekeeping.deactivate();
        staff(EmployeeStatus.ACTIVE);
        assertThatThrownBy(() -> service.listInventory(null, null, 0, 20, null, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userCannotReadOrListInventory() {
        assertThatThrownBy(() -> service.getInventoryItem(88L, 31L, Role.USER))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.listInventory(null, null, 0, 20, null, 31L, Role.USER))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(inventoryRepository, employeeRepository);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void invalidPaginationIsRejected(int page, int size) {
        assertThatThrownBy(() -> service.listInventory(null, null, page, size, null, 1L, Role.ADMIN))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(inventoryRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordHash", "name,sideways", "name,asc,extra", "department.name", "name,"})
    void invalidSortIsRejected(String sort) {
        assertThatThrownBy(() -> service.listInventory(null, null, 0, 20, sort, 1L, Role.ADMIN))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void invalidDepartmentFilterIsRejected() {
        assertThatThrownBy(() -> service.listInventory(-1L, null, 0, 20, null, 1L, Role.ADMIN))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminCanReadInactiveItemWithHistoricalDepartment() {
        InventoryItem item = item();
        item.deactivate();
        housekeeping.deactivate();
        when(inventoryRepository.findById(88L)).thenReturn(Optional.of(item));
        var result = service.getInventoryItem(88L, 1L, Role.ADMIN);
        assertThat(result.getActive()).isFalse();
        assertThat(result.getDepartment().getId()).isEqualTo(3L);
    }

    @Test
    void staffCanReadOwnActiveItem() {
        staff(EmployeeStatus.ACTIVE);
        when(inventoryRepository.findById(88L)).thenReturn(Optional.of(item()));
        assertThat(service.getInventoryItem(88L, 21L, Role.STAFF).getSku()).isEqualTo("HK-TOWEL-BATH");
    }

    @Test
    void staffCannotReadOtherDepartmentItem() {
        staff(EmployeeStatus.ACTIVE);
        InventoryItem other = new InventoryItem("Chairs", "EV-CHAIRS", 3, 2, department(4L, true));
        when(inventoryRepository.findById(88L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.getInventoryItem(88L, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void staffCannotReadInactiveOwnItem() {
        staff(EmployeeStatus.ACTIVE);
        InventoryItem item = item();
        item.deactivate();
        when(inventoryRepository.findById(88L)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.getInventoryItem(88L, 21L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void missingItemReturnsNotFoundForReadUpdateAndDeactivate() {
        UpdateInventoryItemRequest request = updateRequest();
        assertThatThrownBy(() -> service.getInventoryItem(99L, 1L, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateInventoryItem(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deactivateInventoryItem(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restockSetsCanonicalQuantityAndPreservesSkuAndCreatedAt() {
        InventoryItem item = item();
        when(inventoryRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(item));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(housekeeping));
        when(inventoryRepository.save(item)).thenReturn(item);
        var result = service.updateInventoryItem(88L, updateRequest());
        assertThat(result.getQuantity()).isEqualTo(50);
        assertThat(result.getReorderThreshold()).isEqualTo(15);
        assertThat(result.getSku()).isEqualTo("HK-TOWEL-BATH");
        assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 3, 9, 0));
    }

    @Test
    void transferToActiveDepartmentUpdatesRelationship() {
        InventoryItem item = item();
        Department purchasing = department(4L, true);
        when(inventoryRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(item));
        when(departmentRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(purchasing));
        when(inventoryRepository.save(item)).thenReturn(item);
        UpdateInventoryItemRequest request = updateRequest();
        request.setDepartmentId(4L);
        assertThat(service.updateInventoryItem(88L, request).getDepartment().getId()).isEqualTo(4L);
    }

    @Test
    void transferToInactiveDepartmentFailsBeforeAnyMutationEvenWhenDeactivating() {
        InventoryItem item = item();
        when(inventoryRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(item));
        when(departmentRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(department(4L, false)));
        UpdateInventoryItemRequest request = updateRequest();
        request.setDepartmentId(4L);
        request.setActive(false);
        assertThatThrownBy(() -> service.updateInventoryItem(88L, request))
                .isInstanceOf(ConflictException.class);
        assertThat(item.getDepartment()).isSameAs(housekeeping);
        assertThat(item.getQuantity()).isEqualTo(18);
        assertThat(item.getActive()).isTrue();
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void reactivationCannotAssignInactiveDepartment() {
        InventoryItem item = item();
        item.deactivate();
        housekeeping.deactivate();
        when(inventoryRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(item));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(housekeeping));
        UpdateInventoryItemRequest request = updateRequest();
        assertThatThrownBy(() -> service.updateInventoryItem(88L, request))
                .isInstanceOf(ConflictException.class);
        assertThat(item.getActive()).isFalse();
    }

    @Test
    void inactiveHistoryCanRetainItsOriginalInactiveDepartment() {
        InventoryItem item = item();
        item.deactivate();
        housekeeping.deactivate();
        when(inventoryRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(item));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(housekeeping));
        when(inventoryRepository.save(item)).thenReturn(item);
        UpdateInventoryItemRequest request = updateRequest();
        request.setActive(false);
        assertThat(service.updateInventoryItem(88L, request).getDepartment().getId()).isEqualTo(3L);
    }

    @ParameterizedTest
    @CsvSource(value = {"-1,0", "0,-1", "null,0", "0,null"}, nullValues = "null")
    void updateRejectsInvalidCountsBeforeMutating(Integer quantity, Integer threshold) {
        UpdateInventoryItemRequest request = updateRequest();
        request.setQuantity(quantity);
        request.setReorderThreshold(threshold);
        assertThatThrownBy(() -> service.updateInventoryItem(88L, request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void updateRejectsMissingActive() {
        UpdateInventoryItemRequest request = updateRequest();
        request.setActive(null);
        assertThatThrownBy(() -> service.updateInventoryItem(88L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateRejectsMissingDepartmentBeforeMutating() {
        InventoryItem item = item();
        when(inventoryRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(item));
        UpdateInventoryItemRequest request = updateRequest();
        request.setDepartmentId(4L);
        assertThatThrownBy(() -> service.updateInventoryItem(88L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(item.getDepartment()).isSameAs(housekeeping);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void putCanSoftDeactivateWithoutErasingReference() {
        InventoryItem item = item();
        when(inventoryRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(item));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(housekeeping));
        when(inventoryRepository.save(item)).thenReturn(item);
        UpdateInventoryItemRequest request = updateRequest();
        request.setActive(false);
        assertThat(service.updateInventoryItem(88L, request).getActive()).isFalse();
        assertThat(item.getDepartment()).isSameAs(housekeeping);
        verify(inventoryRepository, never()).delete(item);
    }

    @Test
    void deleteOnlySoftDeactivatesAndRemainsSafeOnRepetition() {
        InventoryItem item = item();
        when(inventoryRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(item));
        service.deactivateInventoryItem(88L);
        service.deactivateInventoryItem(88L);
        assertThat(item.getActive()).isFalse();
        assertThat(item.getQuantity()).isEqualTo(18);
        assertThat(item.getSku()).isEqualTo("HK-TOWEL-BATH");
        assertThat(item.getDepartment()).isSameAs(housekeeping);
        verify(inventoryRepository, never()).delete(item);
        verify(inventoryRepository, never()).deleteById(any());
    }

    private void staff(EmployeeStatus status) {
        Employee employee = new Employee("Worker", "worker@example.test", housekeeping, "Attendant", null);
        ReflectionTestUtils.setField(employee, "status", status);
        when(employeeRepository.findByUserId(21L)).thenReturn(Optional.of(employee));
    }

    private Department department(Long id, boolean active) {
        Department department = new Department("Department " + id, null);
        ReflectionTestUtils.setField(department, "id", id);
        if (!active) department.deactivate();
        return department;
    }

    private InventoryItem item() {
        InventoryItem item = new InventoryItem("Bath Towels", "HK-TOWEL-BATH", 18, 20, housekeeping);
        ReflectionTestUtils.setField(item, "id", 88L);
        ReflectionTestUtils.setField(item, "createdAt", LocalDateTime.of(2026, 10, 3, 9, 0));
        return item;
    }

    private CreateInventoryItemRequest createRequest() {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest();
        request.setName("Bath Towels");
        request.setSku("HK-TOWEL-BATH");
        request.setQuantity(18);
        request.setReorderThreshold(20);
        request.setDepartmentId(3L);
        return request;
    }

    private UpdateInventoryItemRequest updateRequest() {
        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest();
        request.setName("Bath Towels");
        request.setQuantity(50);
        request.setReorderThreshold(15);
        request.setDepartmentId(3L);
        request.setActive(true);
        return request;
    }
}
