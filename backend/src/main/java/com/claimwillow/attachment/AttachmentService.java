package com.claimwillow.attachment;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.claimwillow.audit.AuditService;
import com.claimwillow.audit.EventType;
import com.claimwillow.audit.EventVisibility;
import com.claimwillow.claim.Claim;
import com.claimwillow.claim.ClaimAccess;
import com.claimwillow.claim.ClaimAction;
import com.claimwillow.common.ApiException;
import com.claimwillow.user.AppUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Validates, stores and serves claim evidence. Access is checked against the claim on every call. */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final ClaimAccess access;
    private final ClaimAttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final AuditService audit;
    private final Clock clock;
    private final long maxSizeBytes;
    private final int maxPerClaim;

    public AttachmentService(ClaimAccess access, ClaimAttachmentRepository attachments, AttachmentStorage storage,
            AuditService audit, Clock clock, @Value("${app.attachments.max-size-bytes:5242880}") long maxSizeBytes,
            @Value("${app.attachments.max-per-claim:10}") int maxPerClaim) {
        this.access = access;
        this.attachments = attachments;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
        this.maxSizeBytes = maxSizeBytes;
        this.maxPerClaim = maxPerClaim;
    }

    public record StoredFile(ClaimAttachment metadata, InputStream content) {
    }

    @Transactional
    public ClaimAttachment upload(UUID claimId, AppUserDetails user, MultipartFile file) {
        Claim claim = access.visible(claimId, user);
        if (!ClaimAction.available(claim, user).contains(ClaimAction.UPLOAD_ATTACHMENT)) {
            throw ApiException.conflict("UPLOAD_NOT_ALLOWED",
                    "Files can't be added to this claim in its current status.",
                    Map.of("currentStatus", claim.getStatus().name()));
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Choose a file to upload.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "The file exceeds the maximum size of 5 MB.");
        }
        if (attachments.countByClaimId(claim.getId()) >= maxPerClaim) {
            throw ApiException.conflict("TOO_MANY_FILES", "A claim can have at most " + maxPerClaim + " files.",
                    Map.of("limit", maxPerClaim));
        }
        String filename = FileTypeDetector.sanitizeFilename(file.getOriginalFilename());
        String detected;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            byte[] header = in.readNBytes(16);
            detected = FileTypeDetector.detect(header).orElseThrow(AttachmentService::unsupported);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (!FileTypeDetector.extensionMatches(filename, detected)) {
            throw unsupported();
        }

        String key = UUID.randomUUID().toString();
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in, file.getSize());
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store attachment", ex);
        }
        // If the surrounding transaction rolls back, remove the orphaned bytes.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        storage.delete(key);
                    } catch (IOException ex) {
                        log.warn("Could not remove orphaned attachment {}", key);
                    }
                }
            }
        });
        Instant now = Instant.now(clock);
        ClaimAttachment saved = attachments.save(new ClaimAttachment(claim.getId(), user.id(), filename, detected, key,
                file.getSize(), now));
        audit.record(claim, user.id(), EventType.ATTACHMENT_ADDED, null, null,
                "Attachment added: " + filename + " (" + humanSize(file.getSize()) + ").", EventVisibility.ALL,
                Map.of("attachmentId", saved.getId().toString(), "contentType", detected));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ClaimAttachment> list(UUID claimId, AppUserDetails user) {
        Claim claim = access.visible(claimId, user);
        return attachments.findByClaimIdOrderByCreatedAtAsc(claim.getId());
    }

    @Transactional(readOnly = true)
    public StoredFile download(UUID claimId, UUID attachmentId, AppUserDetails user) {
        Claim claim = access.visible(claimId, user);
        ClaimAttachment meta = attachments.findByIdAndClaimId(attachmentId, claim.getId())
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        try {
            return new StoredFile(meta, storage.open(meta.getStorageKey()));
        } catch (IOException ex) {
            log.error("Attachment bytes missing for {}", meta.getId());
            throw ApiException.notFound("Attachment");
        }
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                "Only PDF, PNG and JPEG files are accepted, and the file extension must match its contents.");
    }

    static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.0f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
