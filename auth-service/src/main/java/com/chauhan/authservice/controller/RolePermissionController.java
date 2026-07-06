package com.chauhan.authservice.controller;

import com.chauhan.authservice.entity.Permission;
import com.chauhan.authservice.entity.Role;
import com.chauhan.authservice.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    @PostMapping("/roles")
    public ResponseEntity<Role> createRole(@RequestParam String name) {
        return ResponseEntity.status(201).body(rolePermissionService.createRole(name));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(rolePermissionService.getAllRoles());
    }

    @PostMapping("/permissions")
    public ResponseEntity<Permission> createPermission(@RequestParam String name) {
        return ResponseEntity.status(201).body(rolePermissionService.createPermission(name));
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<Permission>> getAllPermissions() {
        return ResponseEntity.ok(rolePermissionService.getAllPermissions());
    }

    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    public ResponseEntity<Void> assignPermissionToRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        rolePermissionService.assignPermissionToRole(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/roles/{roleId}")
    public ResponseEntity<Void> assignRoleToUser(
            @PathVariable UUID userId,
            @PathVariable UUID roleId) {
        rolePermissionService.assignRoleToUser(userId, roleId);
        return ResponseEntity.noContent().build();
    }
}
