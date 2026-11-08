package com.claimwillow.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A home-contents claim. Status changes go through the methods on this class, which validate
 * transitions with {@link ClaimStateMachine}; the service layer records the audit event in the
 * same transaction.
 */
@Entity
@Table(name = "claims")
public class Claim {

    @Id
    private UUID id;

    @Column(unique = true)
    private String reference;

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "assigned_adjuster_user_id")
    private UUID assignedAdjusterUserId;

    @Column(name = "incident_date")
    private LocalDate incidentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type")
    private IncidentType incidentType;

    private String title;

    private String description;

    @Column(name = "estimated_loss", precision = 12, scale = 2)
    private BigDecimal estimatedLoss;

    @Column(nullable = false, length = 3)
    private String currency = "CAD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Embedded
    private PolicySnapshot snapshot;

    @Column(name = "last_validation_run_id")
    private UUID lastValidationRunId;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Claim() {
    }

    public Claim(UUID ownerUserId) {
        this.id = UUID.randomUUID();
        this.ownerUserId = ownerUserId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateDraft(UUID policyId, LocalDate incidentDate, IncidentType incidentType, String title,
            String description, BigDecimal estimatedLoss) {
        if (status != ClaimStatus.DRAFT) {
            throw new IllegalStateException("Only drafts can be edited");
        }
        this.policyId = policyId;
        this.incidentDate = incidentDate;
        this.incidentType = incidentType;
        this.title = title;
        this.description = description;
        this.estimatedLoss = estimatedLoss;
    }

    public void submit(String reference, PolicySnapshot snapshot, Instant now) {
        ClaimStateMachine.assertTransition(status, ClaimStatus.SUBMITTED);
        this.reference = reference;
        this.snapshot = snapshot;
        this.submittedAt = now;
        this.status = ClaimStatus.SUBMITTED;
    }

    public void assignTo(UUID adjusterId) {
        this.assignedAdjusterUserId = adjusterId;
    }

    public void moveTo(ClaimStatus next, Instant now) {
        ClaimStateMachine.assertTransition(status, next);
        this.status = next;
        if (next.isDecided()) {
            decidedAt = now;
        }
        if (next == ClaimStatus.CLOSED) {
            closedAt = now;
        }
    }

    public void recordValidationRun(UUID runId, Instant at) {
        this.lastValidationRunId = runId;
        this.lastValidatedAt = at;
    }

    public UUID getLastValidationRunId() {
        return lastValidationRunId;
    }

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerUserId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public UUID getAssignedAdjusterUserId() {
        return assignedAdjusterUserId;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public IncidentType getIncidentType() {
        return incidentType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getEstimatedLoss() {
        return estimatedLoss;
    }

    public String getCurrency() {
        return currency;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public PolicySnapshot getSnapshot() {
        return snapshot != null && snapshot.isPresent() ? snapshot : null;
    }

    public long getVersion() {
        return version == null ? 0 : version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
