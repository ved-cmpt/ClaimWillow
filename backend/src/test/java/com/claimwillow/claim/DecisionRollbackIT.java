package com.claimwillow.claim;

import static com.claimwillow.support.TestFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.claimwillow.support.ClaimApi;
import com.claimwillow.support.IntegrationTest;
import com.claimwillow.support.TestFixtures;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * If the audit event cannot be persisted, the decision (status change + decision note) must not be
 * committed either. The failure is real: a temporary CHECK constraint makes PostgreSQL reject the
 * DECISION_RECORDED event insert inside the decision transaction.
 */
@IntegrationTest
class DecisionRollbackIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    ClaimApi api;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void dropFailureInjection() {
        jdbc.execute("alter table claim_events drop constraint if exists test_reject_decision_events");
    }

    private int version(AppUser viewer, UUID id) throws Exception {
        return ClaimApi.read(mvc.perform(get("/api/v1/claims/" + id).with(as(viewer))), "$.version");
    }

    @Test
    void decisionIsRolledBackWhenAuditEventPersistenceFails() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        UUID id = api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        api.postJson("/api/v1/staff/claims/" + id + "/assign", adjuster, Map.of("version", version(adjuster, id)))
                .andExpect(status().isOk());
        api.postJson("/api/v1/staff/claims/" + id + "/start-review", adjuster,
                Map.of("version", version(adjuster, id))).andExpect(status().isOk());
        int v = version(adjuster, id);

        jdbc.execute("alter table claim_events add constraint test_reject_decision_events "
                + "check (event_type <> 'DECISION_RECORDED') not valid");

        api.postJson("/api/v1/staff/claims/" + id + "/decision", adjuster,
                        Map.of("version", v, "decision", "APPROVED", "reason", "Reviewed and within policy terms."))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again."));

        assertThat(jdbc.queryForObject("select status from claims where id = ?", String.class, id))
                .isEqualTo("IN_REVIEW");
        assertThat(jdbc.queryForObject("select decided_at is null from claims where id = ?", Boolean.class, id))
                .isTrue();
        assertThat(jdbc.queryForObject("select version from claims where id = ?", Integer.class, id)).isEqualTo(v);
        assertThat(jdbc.queryForObject(
                "select count(*) from claim_notes where claim_id = ? and note_type = 'DECISION'", Integer.class, id))
                .isZero();

        // Once storage works again the same request succeeds: nothing was half-applied.
        dropFailureInjection();
        api.postJson("/api/v1/staff/claims/" + id + "/decision", adjuster,
                        Map.of("version", v, "decision", "APPROVED", "reason", "Reviewed and within policy terms."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
