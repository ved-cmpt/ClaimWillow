package com.claimwillow.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.claimwillow.claim.Claim;
import com.claimwillow.claim.PolicySnapshot;
import com.claimwillow.common.ApiError.FieldViolation;

/**
 * Pure, deterministic implementations of rules R1–R6. No I/O: callers supply the claim, the policy
 * snapshot, the evidence count and the already owner-scoped duplicate candidates.
 */
public final class ClaimRules {

    public static final BigDecimal MIN_ESTIMATE = new BigDecimal("0.01");
    public static final BigDecimal MAX_ESTIMATE = new BigDecimal("9999999999.99");

    private ClaimRules() {
    }

    /** R1: blocking required-input validation run on submit. Ownership is checked by the caller. */
    public static List<FieldViolation> requiredInputs(Claim claim, LocalDate today) {
        List<FieldViolation> v = new ArrayList<>();
        if (claim.getPolicyId() == null) {
            v.add(new FieldViolation("policyId", "Select the policy this claim is for."));
        }
        if (claim.getIncidentDate() == null) {
            v.add(new FieldViolation("incidentDate", "Incident date is required."));
        } else if (claim.getIncidentDate().isAfter(today)) {
            v.add(new FieldViolation("incidentDate", "Incident date cannot be in the future."));
        }
        if (claim.getIncidentType() == null) {
            v.add(new FieldViolation("incidentType", "Choose whether items were damaged or lost."));
        }
        if (isBlank(claim.getTitle())) {
            v.add(new FieldViolation("title", "A short title is required."));
        }
        if (isBlank(claim.getDescription())) {
            v.add(new FieldViolation("description", "A description of what happened is required."));
        }
        BigDecimal estimate = claim.getEstimatedLoss();
        if (estimate == null) {
            v.add(new FieldViolation("estimatedLoss", "Estimated loss is required."));
        } else if (estimate.compareTo(MIN_ESTIMATE) < 0) {
            v.add(new FieldViolation("estimatedLoss", "Estimated loss must be at least 0.01 CAD."));
        }
        return v;
    }

    /** R2: incident date within the policy period (inclusive). */
    public static List<FindingDraft> policyActivity(Claim claim, PolicySnapshot snapshot) {
        if (snapshot.covers(claim.getIncidentDate())) {
            return List.of();
        }
        return List.of(FindingDraft.of(RuleCode.COVERAGE_REVIEW_REQUIRED, Severity.WARNING,
                "Incident date " + claim.getIncidentDate() + " is outside the policy period "
                        + snapshot.getEffectiveDate() + " to " + snapshot.getExpirationDate()
                        + ". An adjuster must review coverage."));
    }

    /** R3: estimate strictly greater than the coverage limit. */
    public static List<FindingDraft> overLimit(Claim claim, PolicySnapshot snapshot) {
        if (claim.getEstimatedLoss().compareTo(snapshot.getCoverageLimit()) <= 0) {
            return List.of();
        }
        return List.of(FindingDraft.of(RuleCode.OVER_LIMIT, Severity.WARNING,
                "Estimated loss " + money(claim.getEstimatedLoss()) + " exceeds the coverage limit of "
                        + money(snapshot.getCoverageLimit()) + ". The adjuster decides how to proceed."));
    }

    /** R4: estimate at or below the deductible. */
    public static List<FindingDraft> belowDeductible(Claim claim, PolicySnapshot snapshot) {
        if (claim.getEstimatedLoss().compareTo(snapshot.getDeductible()) > 0) {
            return List.of();
        }
        return List.of(FindingDraft.of(RuleCode.BELOW_DEDUCTIBLE, Severity.INFO,
                "Estimated loss " + money(claim.getEstimatedLoss()) + " is at or below the deductible of "
                        + money(snapshot.getDeductible()) + ". Review context only; no payable amount is "
                        + "calculated."));
    }

    /**
     * R5: potential duplicates among candidates. Candidates must already be limited to the same
     * owner; this method re-checks owner, policy and type defensively so another user's claim is
     * never linked.
     */
    public static List<FindingDraft> potentialDuplicates(Claim claim, List<Claim> candidates, RuleSettings settings) {
        List<FindingDraft> findings = new ArrayList<>();
        for (Claim other : candidates) {
            if (other.getId().equals(claim.getId())
                    || !other.getOwnerUserId().equals(claim.getOwnerUserId())
                    || other.getPolicyId() == null || !other.getPolicyId().equals(claim.getPolicyId())
                    || other.getIncidentType() != claim.getIncidentType()
                    || other.getIncidentDate() == null || other.getEstimatedLoss() == null) {
                continue;
            }
            long days = Math.abs(ChronoUnit.DAYS.between(other.getIncidentDate(), claim.getIncidentDate()));
            if (days > settings.duplicateWindowDays()) {
                continue;
            }
            if (!withinTolerance(claim.getEstimatedLoss(), other.getEstimatedLoss(),
                    settings.duplicateAmountTolerance())) {
                continue;
            }
            String ref = other.getReference() != null ? other.getReference() : "an earlier claim";
            findings.add(new FindingDraft(RuleCode.POTENTIAL_DUPLICATE, Severity.WARNING,
                    "Possible duplicate of " + ref + ": same policy and incident type, incident dates " + days
                            + " day(s) apart, estimates " + money(other.getEstimatedLoss()) + " and "
                            + money(claim.getEstimatedLoss()) + ". Not a rejection; the adjuster reviews both.",
                    other.getId()));
        }
        return findings;
    }

    /** |a - b| <= tolerance * max(a, b). */
    public static boolean withinTolerance(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
        BigDecimal diff = a.subtract(b).abs();
        BigDecimal allowed = a.max(b).multiply(tolerance).setScale(2, RoundingMode.HALF_UP);
        return diff.compareTo(allowed) <= 0;
    }

    /** R6: completeness recommendations (non-blocking). */
    public static List<FindingDraft> completeness(Claim claim, long attachmentCount, RuleSettings settings) {
        List<FindingDraft> findings = new ArrayList<>();
        String description = claim.getDescription() == null ? "" : claim.getDescription().trim();
        if (description.length() < settings.minDescriptionLength()) {
            findings.add(FindingDraft.of(RuleCode.INFORMATION_RECOMMENDED, Severity.INFO,
                    "The description has " + description.length() + " characters; at least "
                            + settings.minDescriptionLength() + " are recommended (what happened, where, which "
                            + "items)."));
        }
        if (attachmentCount == 0) {
            findings.add(FindingDraft.of(RuleCode.INFORMATION_RECOMMENDED, Severity.INFO,
                    "No evidence is attached. Photos, receipts or a police report number help the review."));
        }
        return findings;
    }

    public static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.CANADA);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP)) + " CAD";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
