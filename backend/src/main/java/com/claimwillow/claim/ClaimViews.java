package com.claimwillow.claim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.claimwillow.audit.ClaimEvent;
import com.claimwillow.audit.EventVisibility;
import com.claimwillow.claim.ClaimDtos.ClaimDetail;
import com.claimwillow.claim.ClaimDtos.ClaimSummary;
import com.claimwillow.claim.ClaimDtos.FindingResponse;
import com.claimwillow.claim.ClaimDtos.OpenRequest;
import com.claimwillow.claim.ClaimDtos.PolicyRef;
import com.claimwillow.claim.ClaimDtos.RelatedClaim;
import com.claimwillow.claim.ClaimDtos.SnapshotResponse;
import com.claimwillow.claim.ClaimDtos.TimelineEvent;
import com.claimwillow.claim.ClaimDtos.UserSummary;
import com.claimwillow.note.ClaimNote;
import com.claimwillow.policy.Policy;
import com.claimwillow.policy.PolicyRepository;
import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.UserDirectory;
import com.claimwillow.user.UserDirectory.UserRef;
import com.claimwillow.validation.ClaimFinding;
import org.springframework.stereotype.Component;

/** Maps claim entities to API responses, resolving names and policy numbers in bulk. */
@Component
public class ClaimViews {

    private final UserDirectory directory;
    private final PolicyRepository policies;
    private final ClaimRepository claims;

    public ClaimViews(UserDirectory directory, PolicyRepository policies, ClaimRepository claims) {
        this.directory = directory;
        this.policies = policies;
        this.claims = claims;
    }

    public List<ClaimSummary> summaries(List<Claim> list) {
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> policyIds = new HashSet<>();
        for (Claim c : list) {
            userIds.add(c.getOwnerUserId());
            if (c.getAssignedAdjusterUserId() != null) {
                userIds.add(c.getAssignedAdjusterUserId());
            }
            if (c.getPolicyId() != null) {
                policyIds.add(c.getPolicyId());
            }
        }
        Map<UUID, UserRef> users = directory.lookup(userIds);
        Map<UUID, String> policyNumbers = new HashMap<>();
        policies.findAllById(policyIds).forEach(p -> policyNumbers.put(p.getId(), p.getPolicyNumber()));
        List<ClaimSummary> result = new ArrayList<>();
        for (Claim c : list) {
            String policyNumber = c.getSnapshot() != null ? c.getSnapshot().getPolicyNumber()
                    : policyNumbers.get(c.getPolicyId());
            result.add(new ClaimSummary(c.getId(), c.getReference(), c.getTitle(), c.getStatus(),
                    c.getIncidentType(), c.getIncidentDate(), c.getEstimatedLoss(), c.getCurrency(), policyNumber,
                    user(users.get(c.getOwnerUserId())), user(users.get(c.getAssignedAdjusterUserId())),
                    c.getCreatedAt(), c.getSubmittedAt(), c.getUpdatedAt()));
        }
        return result;
    }

    public ClaimDetail detail(Claim c, AppUserDetails viewer, OpenRequest openRequest) {
        Map<UUID, UserRef> users = directory.lookup(
                java.util.stream.Stream.of(c.getOwnerUserId(), c.getAssignedAdjusterUserId())
                        .filter(java.util.Objects::nonNull).toList());
        PolicyRef policyRef = null;
        if (c.getPolicyId() != null) {
            Policy p = policies.findById(c.getPolicyId()).orElse(null);
            if (p != null) {
                policyRef = new PolicyRef(p.getId(), p.getPolicyNumber());
            }
        }
        return new ClaimDetail(c.getId(), c.getReference(), c.getTitle(), c.getDescription(), c.getStatus(),
                c.getIncidentType(), c.getIncidentDate(), c.getEstimatedLoss(), c.getCurrency(), policyRef,
                SnapshotResponse.of(c.getSnapshot()), user(users.get(c.getOwnerUserId())),
                user(users.get(c.getAssignedAdjusterUserId())), c.getVersion(), c.getCreatedAt(),
                c.getSubmittedAt(), c.getUpdatedAt(), c.getDecidedAt(), c.getClosedAt(), c.getLastValidatedAt(),
                ClaimAction.available(c, viewer), openRequest);
    }

    /**
     * Findings with related-claim links. A related claim is only linked if the viewer may open it
     * (staff, or the same owner); otherwise the link is omitted.
     */
    public List<FindingResponse> findings(List<ClaimFinding> findings, AppUserDetails viewer) {
        List<UUID> relatedIds = findings.stream().map(ClaimFinding::getRelatedClaimId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, Claim> related = new HashMap<>();
        claims.findAllById(relatedIds).forEach(r -> related.put(r.getId(), r));
        return findings.stream().map(f -> {
            Claim r = f.getRelatedClaimId() == null ? null : related.get(f.getRelatedClaimId());
            RelatedClaim link = r != null && (viewer.isStaff() || r.isOwnedBy(viewer.id()))
                    ? new RelatedClaim(r.getId(), r.getReference()) : null;
            return new FindingResponse(f.getId(), f.getRuleCode(), f.getRuleCode().shortCode(),
                    f.getRuleCode().title(), f.getRuleVersion(), f.getSeverity(), f.getExplanation(), link,
                    f.getCheckedAt());
        }).toList();
    }

    public List<TimelineEvent> timeline(List<ClaimEvent> events) {
        Map<UUID, UserRef> users = directory.lookup(events.stream().map(ClaimEvent::getActorUserId).toList());
        return events.stream().map(e -> new TimelineEvent(e.getId(), e.getEventType(), e.getFromStatus(),
                e.getToStatus(), e.getSummary(), user(users.get(e.getActorUserId())),
                e.getVisibility() == EventVisibility.STAFF, e.getOccurredAt())).toList();
    }

    public List<StaffDtos.NoteResponse> notes(List<ClaimNote> list) {
        Map<UUID, UserRef> users = directory.lookup(list.stream().map(ClaimNote::getAuthorUserId).toList());
        return list.stream().map(n -> {
            UserRef a = users.get(n.getAuthorUserId());
            return new StaffDtos.NoteResponse(n.getId(), n.getNoteType(), n.getBody(), n.getAuthorUserId(),
                    a == null ? null : a.displayName(), a == null ? null : a.role(), n.getCreatedAt());
        }).toList();
    }

    private static UserSummary user(UserRef ref) {
        return ref == null ? null : new UserSummary(ref.id(), ref.displayName(), ref.role());
    }
}
