# Implementation Plan: Role-Based Access Control (RBAC)

This plan details the steps required to implement Role-Based Access Control (RBAC) within the `auth-service`, including database entities, linking roles/permissions, updating user authorities, exposing management endpoints, and enabling method-level security with `@PreAuthorize`.

---

## 0. Architecture Concept: Why Roles & Permissions?

In a secure enterprise application, hardcoding permission checks bound strictly to high-level "Roles" leads to fragile code. Instead, we use a hybrid **Role-Based Access Control (RBAC)** model where users are assigned **Roles**, and Roles are assigned granular **Permissions**.

```mermaid
graph LR
    User -->|has many| Role
    Role -->|has many| Permission
    Permission -->|protects| Endpoint/Method
```

### A. Coarse-Grained Roles vs. Fine-Grained Permissions
* **Roles (Who you are):** Coarse-grained logical groupings representing user job titles or classifications (e.g., `ROLE_ADMIN`, `ROLE_USER`, `ROLE_MANAGER`).
* **Permissions (What you can do):** Fine-grained operations protecting specific API endpoints or business actions (e.g., `user:read`, `user:write`, `product:delete`, `billing:charge`).

### B. Why This Matters (The Significance)
1. **Decoupling Security Logic from Code:** 
   * Checking roles directly in code (`@PreAuthorize("hasRole('ADMIN')")`) is rigid. If the business decides that a `MANAGER` should also be allowed to execute that method, developers must modify, compile, test, and redeploy the code.
   * By checking permissions instead (`@PreAuthorize("hasAuthority('product:delete')")`), the code remains untouched. We can dynamically grant or revoke permissions to different roles directly in the database without any code changes or redeployments.
2. **Principle of Least Privilege:**
   * It enforces stricter security. For instance, a customer support agent might need to read user profiles (`user:read`) but should not be allowed to edit them (`user:write`). Granular permissions enable this distinction, which is impossible with a single `ROLE_SUPPORT` role unless code-level logic is duplicated.
3. **Dynamic Role Creation:**
   * It enables administrators to create new, custom roles (e.g., `ROLE_AUDITOR`) at runtime and assign them a subset of existing permissions purely through database administration (or control panel APIs).
4. **Compliance & Auditability:**
   * Modern security frameworks (e.g., SOC2, PCI-DSS, ISO 27001) require clear verification of who has authorization to perform sensitive operations. Mapping permissions directly to APIs makes compliance reporting and access control auditing straightforward.

---

## 1. Entities and Database Relationship

We will create a `Permission` entity and establish a Many-to-Many relationship between `Role` and `Permission`.

### A. Create Permission Entity
* **New File to create:** `com.chauhan.authservice.entity.Permission`
* **Content:**
  ```java
  package com.chauhan.authservice.entity;

  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.Id;
  import jakarta.persistence.Table;
  import lombok.*;

  import java.util.UUID;

  @Getter
  @Setter
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  @Entity
  @Table(name = "permissions")
  public class Permission {
      @Id
      @Builder.Default
      private UUID id = UUID.randomUUID();

      @Column(unique = true, nullable = false)
      private String name; // e.g., "user:read", "user:write", "admin:write"
  }
  ```

### B. Update Role Entity
We will establish the `@ManyToMany` association between `Role` and `Permission`.

* **File to modify:** [Role.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/entity/Role.java)
* **Changes:**
  ```java
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id")
  )
  @Builder.Default
  private Set<Permission> permissions = new HashSet<>();
  ```

---

## 2. Spring Security Authority Mapping

To ensure Spring Security recognizes permissions, we must update the `getAuthorities()` method in the `User` class to grant both Roles (prefixed with `ROLE_`) and Permissions (as direct authorities).

* **File to modify:** [User.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/entity/User.java)
* **Method update:**
  ```java
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
      if (this.roles == null) {
          return Collections.emptyList();
      }
      
      Set<GrantedAuthority> authorities = new java.util.HashSet<>();
      for (Role role : roles) {
          authorities.add(new SimpleGrantedAuthority(role.getName())); // e.g., "ROLE_USER"
          if (role.getPermissions() != null) {
              for (Permission permission : role.getPermissions()) {
                  authorities.add(new SimpleGrantedAuthority(permission.getName())); // e.g., "user:read"
              }
          }
      }
      return authorities;
  }
  ```

---

## 3. Repositories

We need repositories to fetch and manage Roles and Permissions.

* **Modify RoleRepository:** [RoleRepository.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/repository/RoleRepository.java) (No modification needed if basic JpaRepository is sufficient, but we can add query helpers if needed).
* **New File to create:** `com.chauhan.authservice.repository.PermissionRepository`
* **Content:**
  ```java
  package com.chauhan.authservice.repository;

  import com.chauhan.authservice.entity.Permission;
  import org.springframework.data.jpa.repository.JpaRepository;
  import java.util.Optional;
  import java.util.UUID;

  public interface PermissionRepository extends JpaRepository<Permission, UUID> {
      Optional<Permission> findByName(String name);
      boolean existsByName(String name);
  }
  ```

---

## 4. Service Layer for Role and Permission Management

Create services to handle business logic for managing roles, permissions, and assignment to users.

### A. Interfaces
* **New Interface:** `com.chauhan.authservice.service.RolePermissionService`
* **Content:**
  * Define methods:
    * `Role createRole(String name);`
    * `Permission createPermission(String name);`
    * `void assignPermissionToRole(UUID roleId, UUID permissionId);`
    * `void assignRoleToUser(UUID userId, UUID roleId);`
    * `List<Role> getAllRoles();`
    * `List<Permission> getAllPermissions();`

### B. Implementation
* **New File to create:** `com.chauhan.authservice.service.impl.RolePermissionServiceImpl`
* **Details:**
  * Inject `UserRepository`, `RoleRepository`, and `PermissionRepository`.
  * Implement CRUD operations and verification checks (e.g. throw `ResourceAlreadyExistsException` or `ResourceNotFoundException`).

---

## 5. REST Controller Endpoints

Expose management endpoints protected by Admin permissions.

* **New File to create:** `com.chauhan.authservice.controller.RolePermissionController`
* **Endpoints:**
  * `POST /api/v1/roles` (Create Role)
  * `GET /api/v1/roles` (Get all Roles)
  * `POST /api/v1/permissions` (Create Permission)
  * `GET /api/v1/permissions` (Get all Permissions)
  * `POST /api/v1/roles/{roleId}/permissions` (Assign permission to role)
  * `POST /api/v1/users/{userId}/roles` (Assign role to user)

---

## 6. Enabling Method Security & `@PreAuthorize`

1. **Verify Config:** Method security is already enabled in [SecurityConfig.java](file:///run/media/sourabh/WorkSpace/Java/Spring%20boot/MicroServices/spring_core_services/auth-service/src/main/java/com/chauhan/authservice/config/SecurityConfig.java) via `@EnableMethodSecurity()`.
2. **Apply `@PreAuthorize`:** Annotate controllers and methods to restrict access based on roles or permissions.
   * **Example 1 (Role-Based):**
     ```java
     @PreAuthorize("hasRole('ADMIN')")
     @PostMapping("/roles")
     public ResponseEntity<Role> createRole(...)
     ```
   * **Example 2 (Permission-Based):**
     ```java
     @PreAuthorize("hasAuthority('user:read')")
     @GetMapping("/users/{id}")
     public ResponseEntity<UserDto> getUserById(...)
     ```

---

## 7. Verification & Testing

* **Integration Tests:** Add tests to verify access is denied (403 Forbidden) when calling endpoints without the required role/authority.
* **Database Migrations:** Update or create migration files for schema alterations (new `permissions` table, `role_permissions` join table, etc.).
