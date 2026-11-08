package com.claimwillow.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.claimwillow.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal account management for synthetic demo accounts (admin only). */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Synthetic demo account management")
public class AdminController {

    private final UserRepository users;

    public AdminController(UserRepository users) {
        this.users = users;
    }

    public record UserRow(UUID id, String username, String email, String displayName, Role role, boolean enabled,
            Instant createdAt) {

        static UserRow of(AppUser u) {
            return new UserRow(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getRole(),
                    u.isEnabled(), u.getCreatedAt());
        }
    }

    public record EnabledRequest(@NotNull(message = "enabled is required.") Boolean enabled) {
    }

    @GetMapping
    @Operation(summary = "All accounts")
    @Transactional(readOnly = true)
    public List<UserRow> list() {
        return users.findAllByOrderByRoleAscDisplayNameAsc().stream().map(UserRow::of).toList();
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Enable or disable an account (not your own)")
    @Transactional
    public UserRow setEnabled(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails admin,
            @Valid @RequestBody EnabledRequest body) {
        if (admin.id().equals(id)) {
            throw ApiException.badRequest("CANNOT_CHANGE_SELF", "You cannot disable your own account.");
        }
        AppUser user = users.findById(id).orElseThrow(() -> ApiException.notFound("Account"));
        user.setEnabled(body.enabled());
        return UserRow.of(users.saveAndFlush(user));
    }
}
