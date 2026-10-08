package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.UserDtos.*;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.*;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUDY NOTE: UserController calls this service with the caller id and Team request/id.
 * We check supervisor/ADMIN rules, load accounts and use AuthService to hash new passwords.
 * For eligibility changes we lock the account and check active work before updating or deactivating.
 * User itself clears refresh state when identity/eligibility changes; transactions keep its writes together.
 * Responses/summaries contain safe fields, not hashes. The first ADMIN uses the explicit local bootstrap,
 * not this API, and this service does not create a second employee model.
 */
@Service
public class UserService {
    private final UserRepository users;
    private final TaskRepository tasks;
    private final AuthService auth;
    public UserService(UserRepository users, TaskRepository tasks, AuthService auth) {
        this.users = users; this.tasks = tasks; this.auth = auth;
    }

    /** Reloads caller eligibility; a browser role label is never authorization. */
    public User requireSupervisor(Long actorId) {
        User actor = account(actorId);
        if (!actor.isActive() || actor.getRole() == User.Role.USER) throw new ForbiddenException("Supervisor access required.");
        return actor;
    }
    public User account(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User account not found."));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(Long actorId) {
        User actor = requireSupervisor(actorId);
        List<User> rows = actor.getRole() == User.Role.ADMIN
                ? users.findAllByOrderByNameAsc() : users.findByRoleOrderByNameAsc(User.Role.USER);
        return rows.stream().map(UserService::response).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long actorId, Long id) {
        User actor = requireSupervisor(actorId);
        User target = account(id);
        if (actor.getRole() == User.Role.MANAGER && target.getRole() != User.Role.USER) {
            throw new ForbiddenException("Managers may view housekeepers only.");
        }
        return response(target);
    }

    @Transactional
    public UserResponse create(Long actorId, CreateUserRequest request) {
        User actor = requireSupervisor(actorId);
        if (request.getRole() == User.Role.ADMIN
                || (actor.getRole() == User.Role.MANAGER && request.getRole() != User.Role.USER)) {
            throw new ForbiddenException("This role cannot be provisioned by the caller.");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("Email is already registered.");
        return response(users.save(new User(request.getName().trim(), email,
                auth.hashPassword(request.getPassword()), request.getRole())));
    }

    @Transactional
    public UserResponse update(Long actorId, Long id, UpdateUserRequest request) {
        requireAdmin(actorId);
        User target = lockedAccount(id);
        if (target.getRole() == User.Role.ADMIN || request.getRole() == User.Role.ADMIN) {
            throw new ForbiddenException("Administrator accounts are bootstrap-managed.");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) throw new ConflictException("Email is already registered.");
        if (!request.getActive() || request.getRole() != target.getRole()) guardActiveWork(id);
        target.update(request.getName().trim(), email, request.getRole(), request.getActive());
        return response(users.save(target));
    }

    @Transactional
    public void deactivate(Long actorId, Long id) {
        requireAdmin(actorId);
        User target = lockedAccount(id);
        if (target.getRole() == User.Role.ADMIN) throw new ForbiddenException("Administrator accounts are bootstrap-managed.");
        guardActiveWork(id);
        target.deactivate();
        users.save(target);
    }

    private void requireAdmin(Long actorId) {
        if (requireSupervisor(actorId).getRole() != User.Role.ADMIN) throw new ForbiddenException("Administrator access required.");
    }
    private User lockedAccount(Long id) {
        return users.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User account not found."));
    }
    private void guardActiveWork(Long id) {
        if (!tasks.findByAssignedUserIdAndStatusIn(id, List.of(Task.Status.ASSIGNED, Task.Status.IN_PROGRESS)).isEmpty()) {
            throw new ConflictException("Reassign or cancel active work before changing worker eligibility.");
        }
    }
    public static UserResponse response(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive());
    }
    public static UserSummary summary(User user) { return new UserSummary(user.getId(), user.getName()); }
}
