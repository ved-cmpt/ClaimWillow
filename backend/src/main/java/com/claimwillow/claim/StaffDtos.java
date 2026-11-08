package com.claimwillow.claim;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.claimwillow.note.NoteType;
import com.claimwillow.user.Role;

/** Requests for staff actions. Every action carries the version the user saw (optimistic locking). */
public final class StaffDtos {

    private StaffDtos() {
    }

    public enum Decision {
        APPROVED,
        REJECTED
    }

    public record AssignRequest(@NotNull(message = "version is required.") Long version, UUID adjusterId) {
    }

    public record StartReviewRequest(@NotNull(message = "version is required.") Long version) {
    }

    public record RequestInfoRequest(
            @NotNull(message = "version is required.") Long version,
            @NotBlank(message = "Explain what information you need.")
            @Size(min = 10, max = 2000, message = "The request must be between 10 and 2000 characters.")
            String reason) {
    }

    public record DecisionRequest(
            @NotNull(message = "version is required.") Long version,
            @NotNull(message = "Choose approve or reject.") Decision decision,
            @NotBlank(message = "A decision note or rejection reason is required.")
            @Size(min = 10, max = 2000, message = "The note must be between 10 and 2000 characters.")
            String reason) {
    }

    public record CloseRequest(@NotNull(message = "version is required.") Long version,
            @Size(max = 2000, message = "The note must be 2000 characters or fewer.") String note) {
    }

    public record NoteRequest(@NotBlank(message = "The note cannot be empty.")
            @Size(max = 4000, message = "The note must be 4000 characters or fewer.") String body) {
    }

    public record ResponseRequest(Long version,
            @Size(max = 4000, message = "The response must be 4000 characters or fewer.") String message) {
    }

    public record NoteResponse(UUID id, NoteType type, String body, UUID authorId, String authorName, Role authorRole,
            Instant createdAt) {
    }

    public record DashboardSummary(long newClaims, long inReview, long needsInfo, long assignedToMe,
            long unassigned) {
    }

    public record AdjusterOption(UUID id, String displayName) {
    }
}
