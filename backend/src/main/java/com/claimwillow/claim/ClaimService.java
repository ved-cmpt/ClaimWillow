package com.claimwillow.claim;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.claimwillow.audit.AuditService;
import com.claimwillow.audit.ClaimEvent;
import com.claimwillow.audit.ClaimEventRepository;
import com.claimwillow.audit.EventType;
import com.claimwillow.audit.EventVisibility;
import com.claimwillow.claim.ClaimDtos.DraftRequest;
import com.claimwillow.claim.ClaimDtos.StatusCount;
import com.claimwillow.claim.ClaimDtos.StatusCounts;
import com.claimwillow.common.ApiError.FieldViolation;
import com.claimwillow.common.ValidationFailedException;
import com.claimwillow.policy.Policy;
import com.claimwillow.policy.PolicyService;
import com.claimwillow.user.AppUserDetails;
import com.claimwillow.validation.ClaimFinding;
import com.claimwillow.validation.ClaimRules;
import com.claimwillow.validation.RuleEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Policyholder claim intake: drafts, submission and read access. */
@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final ClaimAccess access;
    private final PolicyService policyService;
    private final RuleEngine ruleEngine;
    private final AuditService audit;
    private final ClaimEventRepository events;
    private final ReferenceGenerator references;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimAccess access, PolicyService policyService,
            RuleEngine ruleEngine, AuditService audit, ClaimEventRepository events, ReferenceGenerator references,
            Clock clock) {
        this.claims = claims;
        this.access = access;
        this.policyService = policyService;
        this.ruleEngine = ruleEngine;
        this.audit = audit;
        this.events = events;
        this.references = references;
        this.clock = clock;
    }

    @Transactional
    public Claim createDraft(AppUserDetails owner, DraftRequest request) {
        Claim claim = new Claim(owner.id());
        applyDraft(claim, owner, request);
        claim = claims.save(claim);
        audit.record(claim, owner.id(), EventType.DRAFT_CREATED, null, ClaimStatus.DRAFT, "Draft claim created.");
        return claim;
    }

    @Transactional
    public Claim updateDraft(UUID claimId, AppUserDetails owner, DraftRequest request) {
        Claim claim = access.owned(claimId, owner);
        ClaimAccess.checkVersion(claim, request.version());
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw com.claimwillow.common.ApiException.conflict("NOT_A_DRAFT",
                    "Submitted claims cannot be edited. Use a response when the adjuster requests information.",
                    Map.of("currentStatus", claim.getStatus().name()));
        }
        applyDraft(claim, owner, request);
        return claims.saveAndFlush(claim);
    }

    private void applyDraft(Claim claim, AppUserDetails owner, DraftRequest r) {
        if (r.policyId() != null) {
            // Ownership is part of R1: a claimant can only reference their own policy.
            policyService.getOwnedPolicy(r.policyId(), owner);
        }
        claim.updateDraft(r.policyId(), r.incidentDate(), r.incidentType(), trimToNull(r.title()),
                trimToNull(r.description()), r.estimatedLoss() == null ? null : r.estimatedLoss().setScale(2));
    }

    @Transactional
    public Claim submit(UUID claimId, AppUserDetails owner, Long expectedVersion) {
        Claim claim = access.owned(claimId, owner);
        ClaimAccess.checkVersion(claim, expectedVersion);
        ClaimStateMachine.assertTransition(claim.getStatus(), ClaimStatus.SUBMITTED);

        List<FieldViolation> violations = ClaimRules.requiredInputs(claim, LocalDate.now(clock));
        if (!violations.isEmpty()) {
            throw new ValidationFailedException("The claim is not ready to submit. Fix the highlighted fields.",
                    violations);
        }
        Policy policy = policyService.getOwnedPolicy(claim.getPolicyId(), owner);
        Instant now = Instant.now(clock);
        claim.submit(uniqueReference(), PolicySnapshot.of(policy, now), now);

        List<ClaimFinding> findings = ruleEngine.runAndStore(claim);
        audit.record(claim, owner.id(), EventType.SUBMITTED, ClaimStatus.DRAFT, ClaimStatus.SUBMITTED,
                "Claim " + claim.getReference() + " submitted.", EventVisibility.ALL,
                Map.of("reference", claim.getReference(), "policyNumber", policy.getPolicyNumber()));
        recordValidationEvent(claim, owner.id(), findings);
        return claims.saveAndFlush(claim);
    }

    void recordValidationEvent(Claim claim, UUID actorId, List<ClaimFinding> findings) {
        String codes = findings.stream().map(f -> f.getRuleCode().shortCode()).distinct().sorted()
                .collect(Collectors.joining(", "));
        String summary = findings.isEmpty() ? "Automated checks ran: no findings."
                : "Automated checks ran: " + findings.size() + " finding(s) (" + codes + ").";
        audit.record(claim, actorId, EventType.VALIDATION_RUN, null, null, summary, EventVisibility.ALL,
                Map.of("runId", claim.getLastValidationRunId().toString(), "findingCount", findings.size()));
    }

    private String uniqueReference() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = references.next();
            if (!claims.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique claim reference");
    }

    @Transactional(readOnly = true)
    public Page<Claim> mine(AppUserDetails owner, ClaimStatus status, String query, int page, int size) {
        return claims.findMine(owner.id(), status, trimToNull(query), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)));
    }

    @Transactional(readOnly = true)
    public StatusCounts mineSummary(AppUserDetails owner) {
        return toCounts(claims.countByStatusForOwner(owner.id()));
    }

    static StatusCounts toCounts(List<Object[]> rows) {
        Map<ClaimStatus, Long> map = new EnumMap<>(ClaimStatus.class);
        rows.forEach(r -> map.put((ClaimStatus) r[0], (Long) r[1]));
        List<StatusCount> counts = new ArrayList<>();
        long total = 0;
        for (ClaimStatus s : ClaimStatus.values()) {
            long n = map.getOrDefault(s, 0L);
            counts.add(new StatusCount(s, n));
            total += n;
        }
        return new StatusCounts(counts, total);
    }

    @Transactional(readOnly = true)
    public Claim visible(UUID claimId, AppUserDetails viewer) {
        return access.visible(claimId, viewer);
    }

    @Transactional(readOnly = true)
    public List<ClaimFinding> findings(UUID claimId, AppUserDetails viewer) {
        return ruleEngine.latestFindings(access.visible(claimId, viewer));
    }

    @Transactional(readOnly = true)
    public List<ClaimEvent> timeline(UUID claimId, AppUserDetails viewer) {
        Claim claim = access.visible(claimId, viewer);
        if (viewer.isStaff()) {
            return events.findByClaimIdOrderBySeqAsc(claim.getId());
        }
        return events.findByClaimIdAndVisibilityInOrderBySeqAsc(claim.getId(), List.of(EventVisibility.ALL));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
