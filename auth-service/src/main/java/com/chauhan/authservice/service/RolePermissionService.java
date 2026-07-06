package com.chauhan.authservice.service;

import com.chauhan.authservice.entity.Permission;
import com.chauhan.authservice.entity.Role;

import java.util.List;
import java.util.UUID;

public interface RolePermissionService {
    Role createRole(String name);
    Permission createPermission(String name);
    void assignPermissionToRole(UUID roleId, UUID permissionId);
    void assignRoleToUser(UUID userId, UUID roleId);
    List<Role> getAllRoles();
    List<Permission> getAllPermissions();
}
