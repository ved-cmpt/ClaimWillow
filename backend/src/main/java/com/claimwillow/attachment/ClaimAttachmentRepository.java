package com.claimwillow.attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, UUID> {

    List<ClaimAttachment> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    Optional<ClaimAttachment> findByIdAndClaimId(UUID id, UUID claimId);

    long countByClaimId(UUID claimId);
}
