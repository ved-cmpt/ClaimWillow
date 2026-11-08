package com.claimwillow.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import com.claimwillow.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class ClaimStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "DRAFT,SUBMITTED", "SUBMITTED,IN_REVIEW", "IN_REVIEW,NEEDS_INFO", "NEEDS_INFO,IN_REVIEW",
            "IN_REVIEW,APPROVED", "IN_REVIEW,REJECTED", "APPROVED,CLOSED", "REJECTED,CLOSED"})
    void legalTransitionsAreAllowed(ClaimStatus from, ClaimStatus to) {
        ClaimStateMachine.assertTransition(from, to);
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT,IN_REVIEW", "DRAFT,APPROVED", "SUBMITTED,APPROVED", "SUBMITTED,NEEDS_INFO",
            "NEEDS_INFO,APPROVED", "NEEDS_INFO,CLOSED", "APPROVED,REJECTED", "REJECTED,APPROVED",
            "APPROVED,IN_REVIEW", "CLOSED,IN_REVIEW", "CLOSED,DRAFT", "IN_REVIEW,CLOSED", "SUBMITTED,DRAFT"})
    void illegalTransitionsAreConflicts(ClaimStatus from, ClaimStatus to) {
        assertThatThrownBy(() -> ClaimStateMachine.assertTransition(from, to))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("INVALID_TRANSITION");
                    assertThat(ex.getDetails()).containsEntry("currentStatus", from.name());
                });
    }

    @ParameterizedTest
    @EnumSource(ClaimStatus.class)
    void noStatusCanTransitionToItself(ClaimStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    void closedIsTerminalAndDecisionsAreFinal() {
        assertThat(ClaimStatus.CLOSED.allowedNext()).isEmpty();
        assertThat(ClaimStatus.APPROVED.allowedNext()).isEqualTo(EnumSet.of(ClaimStatus.CLOSED));
        assertThat(ClaimStatus.REJECTED.allowedNext()).isEqualTo(Set.of(ClaimStatus.CLOSED));
    }
}
