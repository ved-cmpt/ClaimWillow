package com.claimwillow.audit;

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

import com.claimwillow.claim.ClaimStatus;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/** Append-only audit record. Hibernate treats it as immutable; a DB trigger blocks UPDATE/DELETE. */
@Entity
@Immutable
@Table(name = "claim_events")
public class ClaimEvent implements Persistable<UUID> {

    @Transient
    private boolean newEntity = true;

    @Id
    private UUID id;

    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    private ClaimStatus toStatus;

    @Column(nullable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventVisibility visibility;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "metadata_json")
    private String metadataJson;

    protected ClaimEvent() {
    }

    public ClaimEvent(UUID claimId, UUID actorUserId, EventType eventType, ClaimStatus fromStatus,
            ClaimStatus toStatus, String summary, EventVisibility visibility, Instant occurredAt,
            String metadataJson) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.actorUserId = actorUserId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.summary = summary;
        this.visibility = visibility;
        this.occurredAt = occurredAt;
        this.metadataJson = metadataJson;
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

    public Long getSeq() {
        return seq;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public ClaimStatus getFromStatus() {
        return fromStatus;
    }

    public ClaimStatus getToStatus() {
        return toStatus;
    }

    public String getSummary() {
        return summary;
    }

    public EventVisibility getVisibility() {
        return visibility;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}
