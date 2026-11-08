package com.claimwillow.claim;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.claimwillow.claim.ClaimDtos.ClaimDetail;
import com.claimwillow.claim.ClaimDtos.ClaimSummary;
import com.claimwillow.claim.ClaimDtos.DraftRequest;
import com.claimwillow.claim.ClaimDtos.FindingResponse;
import com.claimwillow.claim.ClaimDtos.StatusCounts;
import com.claimwillow.claim.ClaimDtos.TimelineEvent;
import com.claimwillow.claim.ClaimDtos.VersionRequest;
import com.claimwillow.common.PageResponse;
import com.claimwillow.user.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims", description = "Policyholder claim intake and shared claim views")
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimDetailAssembler detailAssembler;
    private final ClaimViews views;

    public ClaimController(ClaimService claimService, ClaimDetailAssembler detailAssembler, ClaimViews views) {
        this.claimService = claimService;
        this.detailAssembler = detailAssembler;
        this.views = views;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('POLICYHOLDER')")
    @Operation(summary = "The signed-in policyholder's claims (newest activity first)")
    public PageResponse<ClaimSummary> mine(@AuthenticationPrincipal AppUserDetails user,
            @RequestParam(required = false) ClaimStatus status, @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<Claim> result = claimService.mine(user, status, query, page, size);
        List<ClaimSummary> items = views.summaries(result.getContent());
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/mine/summary")
    @PreAuthorize("hasRole('POLICYHOLDER')")
    @Operation(summary = "Counts of the policyholder's claims by status")
    public StatusCounts mineSummary(@AuthenticationPrincipal AppUserDetails user) {
        return claimService.mineSummary(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('POLICYHOLDER')")
    @Operation(summary = "Create a DRAFT claim owned by the signed-in policyholder")
    public ClaimDetail create(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody DraftRequest body) {
        return detailAssembler.detail(claimService.createDraft(user, body), user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One claim (owner, or staff for submitted claims; otherwise 404)")
    public ClaimDetail get(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user) {
        return detailAssembler.detail(claimService.visible(id, user), user);
    }

    @PatchMapping("/{id}/draft")
    @PreAuthorize("hasRole('POLICYHOLDER')")
    @Operation(summary = "Save draft fields (owner, DRAFT only)")
    public ClaimDetail updateDraft(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody DraftRequest body) {
        return detailAssembler.detail(claimService.updateDraft(id, user, body), user);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('POLICYHOLDER')")
    @Operation(summary = "Submit a draft: R1 validation, policy snapshot, rules R2-R6, audit events")
    public ClaimDetail submit(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user,
            @RequestBody(required = false) VersionRequest body) {
        return detailAssembler.detail(claimService.submit(id, user, body == null ? null : body.version()), user);
    }

    @GetMapping("/{id}/findings")
    @Operation(summary = "Findings from the latest validation run")
    public List<FindingResponse> findings(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user) {
        return views.findings(claimService.findings(id, user), user);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Chronological audit events visible to the viewer")
    public List<TimelineEvent> timeline(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user) {
        return views.timeline(claimService.timeline(id, user));
    }
}
