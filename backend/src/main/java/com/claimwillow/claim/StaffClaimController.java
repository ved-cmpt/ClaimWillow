package com.claimwillow.claim;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.claimwillow.claim.ClaimDtos.ClaimDetail;
import com.claimwillow.claim.ClaimDtos.ClaimSummary;
import com.claimwillow.claim.StaffDtos.AdjusterOption;
import com.claimwillow.claim.StaffDtos.AssignRequest;
import com.claimwillow.claim.StaffDtos.CloseRequest;
import com.claimwillow.claim.StaffDtos.DashboardSummary;
import com.claimwillow.claim.StaffDtos.DecisionRequest;
import com.claimwillow.claim.StaffDtos.NoteRequest;
import com.claimwillow.claim.StaffDtos.NoteResponse;
import com.claimwillow.claim.StaffDtos.RequestInfoRequest;
import com.claimwillow.claim.StaffDtos.StartReviewRequest;
import com.claimwillow.common.PageResponse;
import com.claimwillow.user.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAnyRole('ADJUSTER', 'ADMIN')")
@Tag(name = "Staff", description = "Adjuster work queue and review lifecycle")
public class StaffClaimController {

    private final StaffClaimService staff;
    private final ClaimDetailAssembler detailAssembler;
    private final ClaimViews views;

    public StaffClaimController(StaffClaimService staff, ClaimDetailAssembler detailAssembler, ClaimViews views) {
        this.staff = staff;
        this.detailAssembler = detailAssembler;
        this.views = views;
    }

    @GetMapping("/claims")
    @Operation(summary = "Work queue: submitted claims with filters, pagination and sorting")
    public PageResponse<ClaimSummary> queue(@AuthenticationPrincipal AppUserDetails user,
            @RequestParam(required = false) List<ClaimStatus> status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<Claim> result = staff.queue(user, status, assignedTo, query, submittedFrom, submittedTo, sort, page, size);
        return new PageResponse<>(views.summaries(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Queue counts: new, in review, needs info, assigned to me, unassigned")
    public DashboardSummary dashboard(@AuthenticationPrincipal AppUserDetails user) {
        return staff.dashboard(user);
    }

    @GetMapping("/adjusters")
    @Operation(summary = "Staff who can be assigned claims (for filters)")
    public List<AdjusterOption> adjusters() {
        return staff.adjusters().stream().map(u -> new AdjusterOption(u.getId(), u.getDisplayName())).toList();
    }

    @PostMapping("/claims/{id}/assign")
    @Operation(summary = "Assign the claim to yourself (admins may reassign); 409 if already taken or stale")
    public ClaimDetail assign(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody AssignRequest body) {
        return detailAssembler.detail(staff.assign(id, user, body.version(), body.adjusterId()), user);
    }

    @PostMapping("/claims/{id}/start-review")
    @Operation(summary = "SUBMITTED -> IN_REVIEW (assigned adjuster)")
    public ClaimDetail startReview(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody StartReviewRequest body) {
        return detailAssembler.detail(staff.startReview(id, user, body.version()), user);
    }

    @PostMapping("/claims/{id}/request-info")
    @Operation(summary = "IN_REVIEW -> NEEDS_INFO with a mandatory reason")
    public ClaimDetail requestInfo(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody RequestInfoRequest body) {
        return detailAssembler.detail(staff.requestInfo(id, user, body.version(), body.reason()), user);
    }

    @PostMapping("/claims/{id}/decision")
    @Operation(summary = "IN_REVIEW -> APPROVED or REJECTED with a mandatory note/reason")
    public ClaimDetail decide(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody DecisionRequest body) {
        return detailAssembler.detail(staff.decide(id, user, body.version(), body.decision(), body.reason()), user);
    }

    @PostMapping("/claims/{id}/close")
    @Operation(summary = "APPROVED/REJECTED -> CLOSED (assigned adjuster or admin)")
    public ClaimDetail close(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody CloseRequest body) {
        return detailAssembler.detail(staff.close(id, user, body.version(), body.note()), user);
    }

    @PostMapping("/claims/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an internal staff-only note")
    public NoteResponse addNote(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user,
            @Valid @RequestBody NoteRequest body) {
        return views.notes(List.of(staff.addInternalNote(id, user, body.body()))).getFirst();
    }
}
