package com.claimwillow.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated principal stored in the server-side session. Holds no password after login. */
public final class AppUserDetails implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String username;
    private final String email;
    private final String displayName;
    private final Role role;
    private final boolean enabled;
    private transient String passwordHash;

    public AppUserDetails(UUID id, String username, String email, String displayName, Role role, boolean enabled,
            String passwordHash) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public static AppUserDetails from(AppUser user) {
        return new AppUserDetails(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.isEnabled(), user.getPasswordHash());
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }

    public boolean isStaff() {
        return role.isStaff();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
