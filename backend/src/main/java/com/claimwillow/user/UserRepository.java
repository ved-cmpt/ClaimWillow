package com.claimwillow.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    List<AppUser> findByRoleInAndEnabledTrueOrderByDisplayName(List<Role> roles);

    List<AppUser> findAllByOrderByRoleAscDisplayNameAsc();
}
