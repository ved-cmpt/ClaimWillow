package com.claimwillow.validation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/** One persisted rule result. Each validation run has its own run id; rows are never modified. */
@Entity
@Immutable
@Table(name = "claim_findings")
public class ClaimFinding implements Persistable<UUID> {

    @Transient
    private boolean newEntity = true;

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false)
    private RuleCode ruleCode;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String explanation;

    @Column(name = "related_claim_id")
    private UUID relatedClaimId;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected ClaimFinding() {
    }

    public ClaimFinding(UUID claimId, UUID runId, FindingDraft draft, Instant checkedAt) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.runId = runId;
        this.ruleCode = draft.rule();
        this.ruleVersion = draft.rule().version();
        this.severity = draft.severity();
        this.explanation = draft.explanation();
        this.relatedClaimId = draft.relatedClaimId();
        this.checkedAt = checkedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public UUID getRunId() {
        return runId;
    }

    public RuleCode getRuleCode() {
        return ruleCode;
    }

    public int getRuleVersion() {
        return ruleVersion;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getExplanation() {
        return explanation;
    }

    public UUID getRelatedClaimId() {
        return relatedClaimId;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
