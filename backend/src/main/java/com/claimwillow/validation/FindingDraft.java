package com.claimwillow.validation;

import java.util.UUID;

/** Result of evaluating one rule, before it is persisted as a {@link ClaimFinding}. */
public record FindingDraft(RuleCode rule, Severity severity, String explanation, UUID relatedClaimId) {

    public static FindingDraft of(RuleCode rule, Severity severity, String explanation) {
        return new FindingDraft(rule, severity, explanation, null);
    }
}
