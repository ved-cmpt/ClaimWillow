package com.claimwillow.note;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/** Append-only notes store. */
public interface ClaimNoteRepository extends Repository<ClaimNote, UUID> {

    ClaimNote save(ClaimNote note);

    List<ClaimNote> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    List<ClaimNote> findByClaimIdAndNoteTypeInOrderByCreatedAtAsc(UUID claimId, Collection<NoteType> types);

    Optional<ClaimNote> findFirstByClaimIdAndNoteTypeOrderByCreatedAtDesc(UUID claimId, NoteType type);
}
