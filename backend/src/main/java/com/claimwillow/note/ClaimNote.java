package com.claimwillow.note;

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

@Entity
@Immutable
@Table(name = "claim_notes")
public class ClaimNote implements Persistable<UUID> {

    @Transient
    private boolean newEntity = true;

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false)
    private NoteType noteType;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClaimNote() {
    }

    public ClaimNote(UUID claimId, UUID authorUserId, NoteType noteType, String body, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.authorUserId = authorUserId;
        this.noteType = noteType;
        this.body = body;
        this.createdAt = createdAt;
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

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public NoteType getNoteType() {
        return noteType;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
