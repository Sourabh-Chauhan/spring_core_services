package com.chauhan.authservice.service.impl;

import com.chauhan.authservice.entity.Permission;
import com.chauhan.authservice.entity.Role;
import com.chauhan.authservice.entity.User;
import com.chauhan.authservice.exceptions.ResourceAlreadyExistsException;
import com.chauhan.authservice.exceptions.ResourceNotFoundException;
import com.chauhan.authservice.repository.PermissionRepository;
import com.chauhan.authservice.repository.RoleRepository;
import com.chauhan.authservice.repository.UserRepository;
import com.chauhan.authservice.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public Role createRole(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Role name cannot be null or empty");
        }
        
        String roleName = name.trim().toUpperCase();
        // Ensure standard Spring Security role prefix is applied if not already present
        if (!roleName.startsWith("ROLE_")) {
            roleName = "ROLE_" + roleName;
        }

        if (roleRepository.findByName(roleName).isPresent()) {
            throw new ResourceAlreadyExistsException("Role '" + roleName + "' already exists");
        }

        Role role = Role.builder()
                .id(UUID.randomUUID())
                .name(roleName)
                .build();
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public Permission createPermission(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Permission name cannot be null or empty");
        }

        String permissionName = name.trim().toLowerCase();
        if (permissionRepository.existsByName(permissionName)) {
            throw new ResourceAlreadyExistsException("Permission '" + permissionName + "' already exists");
        }

        Permission permission = Permission.builder()
                .id(UUID.randomUUID())
                .name(permissionName)
                .build();
        return permissionRepository.save(permission);
    }

    @Override
    @Transactional
    public void assignPermissionToRole(UUID roleId, UUID permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with id: " + permissionId));

        role.getPermissions().add(permission);
        roleRepository.save(role);
    }

    @Override
    @Transactional
    public void assignRoleToUser(UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        user.getRoles().add(role);
        userRepository.save(user);
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }
}
