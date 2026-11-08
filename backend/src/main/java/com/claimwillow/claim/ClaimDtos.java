package com.claimwillow.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import com.claimwillow.audit.EventType;
import com.claimwillow.user.Role;
import com.claimwillow.validation.RuleCode;
import com.claimwillow.validation.Severity;

/** Request and response shapes for the claim API. */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    /** Draft contents. All fields optional while drafting; R1 enforces completeness on submit. */
    public record DraftRequest(
            UUID policyId,
            LocalDate incidentDate,
            IncidentType incidentType,
            @Size(max = 120, message = "Title must be 120 characters or fewer.") String title,
            @Size(max = 4000, message = "Description must be 4000 characters or fewer.") String description,
            @DecimalMin(value = "0.00", message = "Estimated loss cannot be negative.")
            @DecimalMax(value = "9999999999.99", message = "Estimated loss is too large.")
            @Digits(integer = 10, fraction = 2, message = "Use at most two decimal places.")
            BigDecimal estimatedLoss,
            Long version) {
    }

    public record VersionRequest(Long version) {
    }

    public record UserSummary(UUID id, String displayName, Role role) {
    }

    public record PolicyRef(UUID id, String policyNumber) {
    }

    public record SnapshotResponse(String policyNumber, BigDecimal coverageLimit, BigDecimal deductible,
            LocalDate effectiveDate, LocalDate expirationDate, Instant takenAt) {

        static SnapshotResponse of(PolicySnapshot s) {
            return s == null ? null : new SnapshotResponse(s.getPolicyNumber(), s.getCoverageLimit(),
                    s.getDeductible(), s.getEffectiveDate(), s.getExpirationDate(), s.getTakenAt());
        }
    }

    public record ClaimSummary(
            UUID id,
            String reference,
            String title,
            ClaimStatus status,
            IncidentType incidentType,
            LocalDate incidentDate,
            BigDecimal estimatedLoss,
            String currency,
            String policyNumber,
            UserSummary owner,
            UserSummary assignedAdjuster,
            Instant createdAt,
            Instant submittedAt,
            Instant updatedAt) {
    }

    public record ClaimDetail(
            UUID id,
            String reference,
            String title,
            String description,
            ClaimStatus status,
            IncidentType incidentType,
            LocalDate incidentDate,
            BigDecimal estimatedLoss,
            String currency,
            PolicyRef policy,
            SnapshotResponse policySnapshot,
            UserSummary owner,
            UserSummary assignedAdjuster,
            long version,
            Instant createdAt,
            Instant submittedAt,
            Instant updatedAt,
            Instant decidedAt,
            Instant closedAt,
            Instant lastValidatedAt,
            Set<ClaimAction> availableActions,
            OpenRequest openRequest) {
    }

    /** The adjuster's outstanding request when the claim is NEEDS_INFO. */
    public record OpenRequest(String message, String requestedBy, Instant requestedAt) {
    }

    public record RelatedClaim(UUID id, String reference) {
    }

    public record FindingResponse(
            UUID id,
            RuleCode ruleCode,
            String shortCode,
            String ruleTitle,
            int ruleVersion,
            Severity severity,
            String explanation,
            RelatedClaim relatedClaim,
            Instant checkedAt) {
    }

    public record TimelineEvent(
            UUID id,
            EventType type,
            ClaimStatus fromStatus,
            ClaimStatus toStatus,
            String summary,
            UserSummary actor,
            boolean staffOnly,
            Instant occurredAt) {
    }

    public record StatusCounts(List<StatusCount> counts, long total) {
    }

    public record StatusCount(ClaimStatus status, long count) {
    }

    public record RuleInfo(RuleCode code, String shortCode, int version, String title, String description,
            boolean blocking) {
    }
}
