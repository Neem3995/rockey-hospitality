package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.dto.common.PagedResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class InventoryService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "sku", "quantity", "reorderThreshold", "createdAt"
    );

    private final InventoryItemRepository inventoryRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public InventoryService(InventoryItemRepository inventoryRepository,
                            DepartmentRepository departmentRepository,
                            EmployeeRepository employeeRepository) {
        this.inventoryRepository = inventoryRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryItemResponse> listInventory(
            Long departmentId, Boolean active, int page, int size, String sort,
            Long requesterUserId, Role requesterRole) {
        ensureViewer(requesterRole);
        if (departmentId != null && departmentId <= 0) {
            throw new BadRequestException("Department filter must be positive.");
        }
        Long effectiveDepartment = departmentId;
        Boolean effectiveActive = active;
        if (requesterRole == Role.STAFF) {
            Long ownDepartment = findStaffDepartment(requesterUserId).getId();
            if ((departmentId != null && !departmentId.equals(ownDepartment))
                    || Boolean.FALSE.equals(active)) {
                throw new ForbiddenException("STAFF may view only active inventory in its department.");
            }
            effectiveDepartment = ownDepartment;
            effectiveActive = true;
        }
        Page<InventoryItem> items = inventoryRepository.search(
                effectiveDepartment, effectiveActive, pageRequest(page, size, sort)
        );
        return new PagedResponse<>(
                items.getContent().stream().map(this::toResponse).toList(),
                items.getNumber(), items.getSize(), items.getTotalElements(),
                items.getTotalPages(), items.isLast()
        );
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getInventoryItem(Long itemId, Long requesterUserId, Role requesterRole) {
        ensureViewer(requesterRole);
        Department ownDepartment = requesterRole == Role.STAFF
                ? findStaffDepartment(requesterUserId) : null;
        InventoryItem item = findItem(itemId);
        if (ownDepartment != null && (!Boolean.TRUE.equals(item.getActive())
                || !ownDepartment.getId().equals(item.getDepartment().getId()))) {
            throw new ForbiddenException("STAFF may view only active inventory in its department.");
        }
        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse createInventoryItem(CreateInventoryItemRequest request) {
        String name = normalizeName(request.getName());
        String sku = normalizeSku(request.getSku());
        validateCounts(request.getQuantity(), request.getReorderThreshold());
        Department department = findDepartmentForUpdate(request.getDepartmentId(), true);
        if (inventoryRepository.existsBySkuIgnoreCase(sku)) {
            throw new ConflictException("Inventory SKU is already registered.");
        }
        InventoryItem item = new InventoryItem(name, sku, request.getQuantity(),
                request.getReorderThreshold(), department);
        return toResponse(inventoryRepository.save(item));
    }

    @Transactional
    public InventoryItemResponse updateInventoryItem(Long itemId, UpdateInventoryItemRequest request) {
        String name = normalizeName(request.getName());
        validateCounts(request.getQuantity(), request.getReorderThreshold());
        if (request.getActive() == null) {
            throw new BadRequestException("Active is required.");
        }
        InventoryItem item = findItemForUpdate(itemId);
        boolean moving = !item.getDepartment().getId().equals(request.getDepartmentId());
        // Historical inactive items may retain their inactive department, but cannot move into one.
        Department department = findDepartmentForUpdate(
                request.getDepartmentId(), moving || request.getActive()
        );
        item.updateDetails(name, request.getQuantity(), request.getReorderThreshold(),
                department, request.getActive());
        return toResponse(inventoryRepository.save(item));
    }

    @Transactional
    public void deactivateInventoryItem(Long itemId) {
        InventoryItem item = findItemForUpdate(itemId);
        item.deactivate();
        inventoryRepository.save(item);
    }

    private Department findDepartmentForUpdate(Long departmentId, boolean requireActive) {
        if (departmentId == null || departmentId <= 0) {
            throw new BadRequestException("Department ID must be positive.");
        }
        // Shared with Department deactivation so an active assignment cannot race its guard.
        Department department = departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id " + departmentId + "."
                ));
        if (requireActive && !Boolean.TRUE.equals(department.getActive())) {
            throw new ConflictException("Inventory cannot be assigned to an inactive department.");
        }
        return department;
    }

    private Department findStaffDepartment(Long requesterUserId) {
        Employee employee = employeeRepository.findByUserId(requesterUserId)
                .orElseThrow(() -> new ForbiddenException("STAFF requires an active employee department."));
        if (employee.getStatus() != EmployeeStatus.ACTIVE
                || !Boolean.TRUE.equals(employee.getDepartment().getActive())) {
            throw new ForbiddenException("STAFF requires an active employee department.");
        }
        return employee.getDepartment();
    }

    private void ensureViewer(Role role) {
        if (role != Role.STAFF && role != Role.ADMIN) {
            throw new ForbiddenException("Inventory access is forbidden.");
        }
    }

    private InventoryItem findItem(Long itemId) {
        return inventoryRepository.findById(itemId).orElseThrow(() -> itemNotFound(itemId));
    }

    private InventoryItem findItemForUpdate(Long itemId) {
        return inventoryRepository.findByIdForUpdate(itemId).orElseThrow(() -> itemNotFound(itemId));
    }

    private ResourceNotFoundException itemNotFound(Long itemId) {
        return new ResourceNotFoundException("Inventory item not found with id " + itemId + ".");
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.length() < 2 || name.length() > 120) {
            throw new BadRequestException("Name must be between 2 and 120 characters.");
        }
        return name;
    }

    private String normalizeSku(String value) {
        String sku = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9-]{1,40}")) {
            throw new BadRequestException("SKU must contain 1-40 letters, numbers, or hyphens.");
        }
        return sku;
    }

    private void validateCounts(Integer quantity, Integer threshold) {
        if (quantity == null || quantity < 0) {
            throw new BadRequestException("Quantity must be zero or greater.");
        }
        if (threshold == null || threshold < 0) {
            throw new BadRequestException("Reorder threshold must be zero or greater.");
        }
    }

    private PageRequest pageRequest(int page, int size, String sortValue) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Page must be zero or greater; page size must be between 1 and 100.");
        }
        String[] parts = sortValue == null || sortValue.isBlank()
                ? new String[]{"name", "asc"} : sortValue.split(",", -1);
        if (parts.length > 2 || !ALLOWED_SORT_FIELDS.contains(parts[0])) {
            throw new BadRequestException("Inventory sort is invalid.");
        }
        try {
            Sort.Direction direction = parts.length == 1
                    ? Sort.Direction.ASC : Sort.Direction.fromString(parts[1]);
            return PageRequest.of(page, size, Sort.by(direction, parts[0]));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Inventory sort direction is invalid.");
        }
    }

    private InventoryItemResponse toResponse(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(), item.getName(), item.getSku(), item.getQuantity(),
                item.getReorderThreshold(),
                new DepartmentSummary(item.getDepartment().getId(), item.getDepartment().getName()),
                item.getActive(), item.getCreatedAt(), item.getUpdatedAt()
        );
    }
}
