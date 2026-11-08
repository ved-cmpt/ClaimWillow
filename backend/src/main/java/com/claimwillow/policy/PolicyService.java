package com.claimwillow.policy;

import java.util.List;
import java.util.UUID;

import com.claimwillow.common.ApiException;
import com.claimwillow.user.AppUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyRepository policies;

    public PolicyService(PolicyRepository policies) {
        this.policies = policies;
    }

    public List<Policy> policiesOwnedBy(AppUserDetails user) {
        return policies.findByPolicyholderUserIdOrderByEffectiveDateDesc(user.id());
    }

    /** Owners see their own policies; staff see any. Everyone else gets 404 (no existence leak). */
    public Policy getVisiblePolicy(UUID id, AppUserDetails user) {
        if (user.isStaff()) {
            return policies.findById(id).orElseThrow(() -> ApiException.notFound("Policy"));
        }
        return policies.findByIdAndPolicyholderUserId(id, user.id())
                .orElseThrow(() -> ApiException.notFound("Policy"));
    }

    /** For claim intake: the policy must belong to the claimant. */
    public Policy getOwnedPolicy(UUID id, AppUserDetails owner) {
        return policies.findByIdAndPolicyholderUserId(id, owner.id())
                .orElseThrow(() -> ApiException.notFound("Policy"));
    }
}
