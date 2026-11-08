package com.claimwillow.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.Role;
import org.junit.jupiter.api.Test;

class ClaimActionTest {

    static AppUserDetails user(Role role) {
        return new AppUserDetails(UUID.randomUUID(), role.name() + "@example.com", role.name(), role, true, null);
    }

    static Claim submittedClaim(UUID owner) {
        Claim c = new Claim(owner);
        c.updateDraft(UUID.randomUUID(), LocalDate.of(2026, 3, 1), IncidentType.DAMAGE, "t", "d",
                new BigDecimal("10.00"));
        c.submit("CW-2026-AAAAAA", new PolicySnapshot("HC-1", new BigDecimal("100"), new BigDecimal("1"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), Instant.now()), Instant.now());
        return c;
    }

    @Test
    void ownerActionsDependOnStatus() {
        AppUserDetails owner = user(Role.POLICYHOLDER);
        Claim draft = new Claim(owner.id());
        assertThat(ClaimAction.available(draft, owner)).containsExactlyInAnyOrder(ClaimAction.EDIT_DRAFT,
                ClaimAction.SUBMIT, ClaimAction.UPLOAD_ATTACHMENT);
        Claim submitted = submittedClaim(owner.id());
        assertThat(ClaimAction.available(submitted, owner)).isEmpty();
        submitted.assignTo(UUID.randomUUID());
        submitted.moveTo(ClaimStatus.IN_REVIEW, Instant.now());
        submitted.moveTo(ClaimStatus.NEEDS_INFO, Instant.now());
        assertThat(ClaimAction.available(submitted, owner)).containsExactlyInAnyOrder(ClaimAction.RESPOND,
                ClaimAction.UPLOAD_ATTACHMENT);
    }

    @Test
    void anotherPolicyholderGetsNothing() {
        Claim c = new Claim(UUID.randomUUID());
        assertThat(ClaimAction.available(c, user(Role.POLICYHOLDER))).isEmpty();
    }

    @Test
    void adjusterSeesAssignOnlyWhenUnassignedAndWorkActionsOnlyWhenAssigned() {
        AppUserDetails dana = user(Role.ADJUSTER);
        AppUserDetails sam = user(Role.ADJUSTER);
        Claim c = submittedClaim(UUID.randomUUID());
        assertThat(ClaimAction.available(c, dana)).contains(ClaimAction.ASSIGN_TO_ME)
                .doesNotContain(ClaimAction.START_REVIEW);
        c.assignTo(dana.id());
        assertThat(ClaimAction.available(c, dana)).contains(ClaimAction.START_REVIEW)
                .doesNotContain(ClaimAction.ASSIGN_TO_ME);
        assertThat(ClaimAction.available(c, sam)).doesNotContain(ClaimAction.ASSIGN_TO_ME, ClaimAction.START_REVIEW);
        c.moveTo(ClaimStatus.IN_REVIEW, Instant.now());
        assertThat(ClaimAction.available(c, dana)).contains(ClaimAction.REQUEST_INFO, ClaimAction.DECIDE);
        c.moveTo(ClaimStatus.REJECTED, Instant.now());
        assertThat(ClaimAction.available(c, dana)).contains(ClaimAction.CLOSE).doesNotContain(ClaimAction.DECIDE);
        assertThat(ClaimAction.available(c, user(Role.ADMIN))).contains(ClaimAction.CLOSE);
        c.moveTo(ClaimStatus.CLOSED, Instant.now());
        assertThat(ClaimAction.available(c, dana)).isEmpty();
    }

    @Test
    void staffGetNoActionsOnDrafts() {
        assertThat(ClaimAction.available(new Claim(UUID.randomUUID()), user(Role.ADMIN))).isEmpty();
    }
}
