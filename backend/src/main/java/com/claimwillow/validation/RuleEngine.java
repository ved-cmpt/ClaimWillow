package com.claimwillow.validation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.claimwillow.attachment.ClaimAttachmentRepository;
import com.claimwillow.claim.Claim;
import com.claimwillow.claim.ClaimRepository;
import com.claimwillow.claim.PolicySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs R2–R6 against a submitted claim and stores the findings as a new run. */
@Service
public class RuleEngine {

    private final ClaimRepository claims;
    private final ClaimAttachmentRepository attachments;
    private final ClaimFindingRepository findings;
    private final RuleSettings settings;
    private final Clock clock;

    public RuleEngine(ClaimRepository claims, ClaimAttachmentRepository attachments, ClaimFindingRepository findings,
            RuleSettings settings, Clock clock) {
        this.claims = claims;
        this.attachments = attachments;
        this.findings = findings;
        this.settings = settings;
        this.clock = clock;
    }

    public List<FindingDraft> evaluate(Claim claim) {
        PolicySnapshot snapshot = claim.getSnapshot();
        if (snapshot == null) {
            throw new IllegalStateException("Rules run only on submitted claims with a policy snapshot");
        }
        List<Claim> candidates = claims.findDuplicateCandidates(claim.getOwnerUserId(), claim.getPolicyId(),
                claim.getIncidentType(), claim.getId(),
                claim.getIncidentDate().minusDays(settings.duplicateWindowDays()),
                claim.getIncidentDate().plusDays(settings.duplicateWindowDays()));
        List<FindingDraft> result = new ArrayList<>();
        result.addAll(ClaimRules.policyActivity(claim, snapshot));
        result.addAll(ClaimRules.overLimit(claim, snapshot));
        result.addAll(ClaimRules.belowDeductible(claim, snapshot));
        result.addAll(ClaimRules.potentialDuplicates(claim, candidates, settings));
        result.addAll(ClaimRules.completeness(claim, attachments.countByClaimId(claim.getId()), settings));
        return result;
    }

    public List<ClaimFinding> latestFindings(Claim claim) {
        if (claim.getLastValidationRunId() == null) {
            return List.of();
        }
        return findings.findByClaimIdAndRunIdOrderBySeverityDescRuleCodeAsc(claim.getId(),
                claim.getLastValidationRunId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ClaimFinding> runAndStore(Claim claim) {
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        claim.recordValidationRun(runId, now);
        return evaluate(claim).stream()
                .map(draft -> findings.save(new ClaimFinding(claim.getId(), runId, draft, now)))
                .toList();
    }
}
