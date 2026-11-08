package com.claimwillow.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves user ids to display names for API responses (never exposes emails to other users). */
@Service
@Transactional(readOnly = true)
public class UserDirectory {

    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    public record UserRef(UUID id, String displayName, Role role) {
    }

    public Map<UUID, UserRef> lookup(Collection<UUID> ids) {
        Map<UUID, UserRef> result = new HashMap<>();
        users.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList())
                .forEach(u -> result.put(u.getId(), new UserRef(u.getId(), u.getDisplayName(), u.getRole())));
        return result;
    }

    public UserRef get(UUID id) {
        if (id == null) {
            return null;
        }
        return users.findById(id).map(u -> new UserRef(u.getId(), u.getDisplayName(), u.getRole())).orElse(null);
    }
}
