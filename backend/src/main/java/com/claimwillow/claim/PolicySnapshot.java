package com.claimwillow.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.claimwillow.policy.Policy;

/** Immutable copy of the policy terms taken when the claim is submitted. */
@Embeddable
public class PolicySnapshot {

    @Column(name = "snapshot_policy_number")
    private String policyNumber;

    @Column(name = "snapshot_coverage_limit", precision = 12, scale = 2)
    private BigDecimal coverageLimit;

    @Column(name = "snapshot_deductible", precision = 12, scale = 2)
    private BigDecimal deductible;

    @Column(name = "snapshot_effective_date")
    private LocalDate effectiveDate;

    @Column(name = "snapshot_expiration_date")
    private LocalDate expirationDate;

    @Column(name = "snapshot_taken_at")
    private Instant takenAt;

    protected PolicySnapshot() {
    }

    public PolicySnapshot(String policyNumber, BigDecimal coverageLimit, BigDecimal deductible,
            LocalDate effectiveDate, LocalDate expirationDate, Instant takenAt) {
        this.policyNumber = policyNumber;
        this.coverageLimit = coverageLimit;
        this.deductible = deductible;
        this.effectiveDate = effectiveDate;
        this.expirationDate = expirationDate;
        this.takenAt = takenAt;
    }

    public static PolicySnapshot of(Policy policy, Instant takenAt) {
        return new PolicySnapshot(policy.getPolicyNumber(), policy.getCoverageLimit(), policy.getDeductible(),
                policy.getEffectiveDate(), policy.getExpirationDate(), takenAt);
    }

    public boolean covers(LocalDate date) {
        return date != null && !date.isBefore(effectiveDate) && !date.isAfter(expirationDate);
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public BigDecimal getCoverageLimit() {
        return coverageLimit;
    }

    public BigDecimal getDeductible() {
        return deductible;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public boolean isPresent() {
        return policyNumber != null;
    }
}
