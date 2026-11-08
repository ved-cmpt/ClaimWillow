package com.claimwillow.validation;

/**
 * Deterministic claim rules. Each rule is named, versioned and explained; the explanations are
 * served to the UI through the rule catalog. Bump {@code version} when a rule's logic changes.
 */
public enum RuleCode {
    REQUIRED_INPUTS("R1", 1, "Required inputs",
            "Incident date (not in the future), title, description, a policy you hold, an incident type and an "
                    + "estimate of at least 0.01 CAD are required. Submission is blocked until they are valid."),
    COVERAGE_REVIEW_REQUIRED("R2", 1, "Policy active on incident date",
            "The incident date must fall within the policy's effective and expiration dates (inclusive). "
                    + "If not, an adjuster must review coverage. This is not an automatic rejection."),
    OVER_LIMIT("R3", 1, "Estimate vs coverage limit",
            "Flags an estimate greater than the coverage limit in the policy snapshot. The adjuster still decides."),
    BELOW_DEDUCTIBLE("R4", 1, "Deductible",
            "Flags an estimate at or below the deductible. Shown as review context only; no payable amount is "
                    + "calculated."),
    POTENTIAL_DUPLICATE("R5", 1, "Potential duplicate",
            "Same policyholder, same policy, same incident type, incident dates within the configured window and "
                    + "estimates within the configured tolerance. Never an automatic rejection or fraud finding."),
    INFORMATION_RECOMMENDED("R6", 1, "Completeness",
            "Recommends more detail when the description is short or no evidence is attached. Does not block "
                    + "submission.");

    private final String shortCode;
    private final int version;
    private final String title;
    private final String description;

    RuleCode(String shortCode, int version, String title, String description) {
        this.shortCode = shortCode;
        this.version = version;
        this.title = title;
        this.description = description;
    }

    public String shortCode() {
        return shortCode;
    }

    public int version() {
        return version;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
