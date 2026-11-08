package com.claimwillow.attachment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** Metadata for a stored evidence file. Bytes live in private storage under {@code storageKey}. */
@Entity
@Table(name = "claim_attachments")
public class ClaimAttachment implements Persistable<UUID> {

    @Transient
    private boolean newEntity = true;

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "detected_content_type", nullable = false)
    private String detectedContentType;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClaimAttachment() {
    }

    public ClaimAttachment(UUID claimId, UUID uploadedByUserId, String originalFilename, String detectedContentType,
            String storageKey, long sizeBytes, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.uploadedByUserId = uploadedByUserId;
        this.originalFilename = originalFilename;
        this.detectedContentType = detectedContentType;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
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

    public UUID getUploadedByUserId() {
        return uploadedByUserId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getDetectedContentType() {
        return detectedContentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
