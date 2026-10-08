package com.rockey.hospitality.dto;

import com.rockey.hospitality.entity.*;
import jakarta.validation.constraints.*;

/**
 * STUDY NOTE: These objects carry Team form input and safe account output.
 * UserController receives validated requests; UserService checks which role and target are allowed.
 * Requesting a role does not grant it. UserResponse and UserSummary omit password and refresh hashes.
 * The grouping class keeps related DTOs together; none of these classes creates a database table
 * or substitutes for the account rules in UserService.
 */
public final class UserDtos {
    private UserDtos() { }
    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class CreateUserRequest {
        @NotBlank @Size(min = 2, max = 100)
        private String name;
        @NotBlank @Email @Size(max = 120)
        private String email;
        @NotBlank @Size(min = 8, max = 72)
        private String password;
        @NotNull
        private User.Role role;
        /** Jackson constructs the request, then populates properties. */
        public CreateUserRequest() { }
        public CreateUserRequest(String name, String email, String password, User.Role role) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.role = role;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public User.Role getRole() { return role; }
        public void setRole(User.Role role) { this.role = role; }
    }

    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class UpdateUserRequest {
        @NotBlank @Size(min = 2, max = 100)
        private String name;
        @NotBlank @Email @Size(max = 120)
        private String email;
        @NotNull
        private User.Role role;
        @NotNull
        private Boolean active;
        /** Jackson constructs the request, then populates properties. */
        public UpdateUserRequest() { }
        public UpdateUserRequest(String name, String email, User.Role role, Boolean active) {
            this.name = name;
            this.email = email;
            this.role = role;
            this.active = active;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public User.Role getRole() { return role; }
        public void setRole(User.Role role) { this.role = role; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    /** Safe JSON output, without password hashes or refresh session data. */
    public static class UserResponse {
        private final Long id;
        private final String name;
        private final String email;
        private final User.Role role;
        private final boolean active;
        public UserResponse(Long id, String name, String email, User.Role role, boolean active) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.role = role;
            this.active = active;
        }
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public User.Role getRole() { return role; }
        public boolean getActive() { return active; }
    }

    /** Safe JSON output, without password hashes or refresh session data. */
    public static class UserSummary {
        private final Long id;
        private final String name;
        public UserSummary(Long id, String name) {
            this.id = id;
            this.name = name;
        }
        public Long getId() { return id; }
        public String getName() { return name; }
    }
}
