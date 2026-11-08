package com.claimwillow.audit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/** Append-only: exposes insert and read operations only (no update or delete methods). */
public interface ClaimEventRepository extends Repository<ClaimEvent, UUID> {

    ClaimEvent save(ClaimEvent event);

    List<ClaimEvent> findByClaimIdOrderBySeqAsc(UUID claimId);

    List<ClaimEvent> findByClaimIdAndVisibilityInOrderBySeqAsc(UUID claimId, Collection<EventVisibility> visibility);

    long countByClaimId(UUID claimId);
}
