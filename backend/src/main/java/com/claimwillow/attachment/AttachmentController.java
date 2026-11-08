package com.claimwillow.attachment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.UserDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/attachments")
@Tag(name = "Attachments", description = "Private evidence files (PDF, PNG, JPEG; 5 MB max)")
public class AttachmentController {

    private final AttachmentService service;
    private final UserDirectory directory;

    public AttachmentController(AttachmentService service, UserDirectory directory) {
        this.service = service;
        this.directory = directory;
    }

    /** Attachment metadata. The storage key is deliberately not part of the API. */
    public record AttachmentResponse(UUID id, String filename, String contentType, long sizeBytes,
            String uploadedBy, Instant createdAt, String downloadUrl) {
    }

    private List<AttachmentResponse> toResponses(UUID claimId, List<ClaimAttachment> list) {
        Map<UUID, UserDirectory.UserRef> users = directory.lookup(
                list.stream().map(ClaimAttachment::getUploadedByUserId).toList());
        return list.stream().map(a -> new AttachmentResponse(a.getId(), a.getOriginalFilename(),
                a.getDetectedContentType(), a.getSizeBytes(),
                users.containsKey(a.getUploadedByUserId()) ? users.get(a.getUploadedByUserId()).displayName() : null,
                a.getCreatedAt(), "/api/v1/claims/" + claimId + "/attachments/" + a.getId())).toList();
    }

    @GetMapping
    @Operation(summary = "List evidence on a claim (owner or staff)")
    public List<AttachmentResponse> list(@PathVariable UUID claimId, @AuthenticationPrincipal AppUserDetails user) {
        return toResponses(claimId, service.list(claimId, user));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload evidence (owner while DRAFT/NEEDS_INFO; staff while open)")
    public AttachmentResponse upload(@PathVariable UUID claimId, @AuthenticationPrincipal AppUserDetails user,
            @RequestParam("file") MultipartFile file) {
        return toResponses(claimId, List.of(service.upload(claimId, user, file))).getFirst();
    }

    @GetMapping("/{attachmentId}")
    @Operation(summary = "Download evidence (authenticated, role-checked stream)")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID claimId, @PathVariable UUID attachmentId,
            @AuthenticationPrincipal AppUserDetails user) {
        AttachmentService.StoredFile file = service.download(claimId, attachmentId, user);
        ClaimAttachment meta = file.metadata();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(meta.getDetectedContentType()))
                .contentLength(meta.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(meta.getOriginalFilename(), java.nio.charset.StandardCharsets.UTF_8).build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(new InputStreamResource(file.content()));
    }
}
