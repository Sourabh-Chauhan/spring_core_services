package com.chauhan.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

/**
 * Represents a user in the system.
 *
 * This class is the core of the security model. It implements Spring Security's {@link UserDetails}
 * interface, which allows the framework to seamlessly integrate with this application's user
 * data for authentication and authorization purposes.
 *
 * The fields and methods provided by UserDetails (e.g., getAuthorities, isEnabled) are
 * automatically used by Spring's AuthenticationManager during the login process.
 */
@Entity
@Table(name = "users")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(unique = true, nullable = false)
    private UUID id;

    @Column(name = "user_email", unique = true, length = 300)
    private String email;

    @Column(name = "user_name", length = 500)
    private String name;

    private String password;
    private String image;

    @Builder.Default
    private boolean enable = true;

    @Builder.Default
    private boolean emailVerified = false;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Provider provider = Provider.LOCAL;
    private String providerId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Returns the authorities granted to the user. This is a core part of the UserDetails contract.
     * Spring Security uses this method to determine the user's roles and permissions.
     * @return A collection of GrantedAuthority objects.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.roles == null) {
            return Collections.emptyList();
        }
        return roles.stream().map(role -> new SimpleGrantedAuthority(role.getName())).toList();
    }

    /**
     * Returns the password used to authenticate the user.
     * @return The user's hashed password.
     */
    @Override
    public @Nullable String getPassword() {
        return this.password;
    }

    /**
     * Returns the username used to authenticate the user. In this application, the email is used as the username.
     * @return The user's email address.
     */
    @Override
    public String getUsername() {
        return this.email;
    }

    /**
     * Indicates whether the user's account is enabled or disabled.
     * Spring Security will automatically block authentication for users where this method returns false.
     * @return true if the user is enabled, false otherwise.
     */
    @Override
    public boolean isEnabled() {
        return this.enable;
    }
}