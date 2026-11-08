package com.claimwillow.policy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    List<Policy> findByPolicyholderUserIdOrderByEffectiveDateDesc(UUID policyholderUserId);

    Optional<Policy> findByIdAndPolicyholderUserId(UUID id, UUID policyholderUserId);

    boolean existsByPolicyNumber(String policyNumber);
}
