package com.claimwillow.validation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/** Append-only findings store. */
public interface ClaimFindingRepository extends Repository<ClaimFinding, UUID> {

    ClaimFinding save(ClaimFinding finding);

    List<ClaimFinding> findByClaimIdOrderByCheckedAtAsc(UUID claimId);

    List<ClaimFinding> findByClaimIdAndRunIdOrderBySeverityDescRuleCodeAsc(UUID claimId, UUID runId);
}
