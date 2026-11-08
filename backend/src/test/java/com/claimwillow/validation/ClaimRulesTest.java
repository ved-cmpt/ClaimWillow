package com.claimwillow.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.claimwillow.claim.Claim;
import com.claimwillow.claim.IncidentType;
import com.claimwillow.claim.PolicySnapshot;
import com.claimwillow.common.ApiError.FieldViolation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClaimRulesTest {

    static final LocalDate EFFECTIVE = LocalDate.of(2026, 1, 1);
    static final LocalDate EXPIRES = LocalDate.of(2026, 12, 31);
    static final UUID OWNER = UUID.randomUUID();
    static final UUID POLICY = UUID.randomUUID();
    static final RuleSettings SETTINGS = RuleSettings.defaults();

    static PolicySnapshot snapshot(String limit, String deductible) {
        return new PolicySnapshot("HC-1", new BigDecimal(limit), new BigDecimal(deductible), EFFECTIVE, EXPIRES,
                Instant.now());
    }

    static Claim claim(UUID owner, UUID policy, LocalDate incident, IncidentType type, String estimate,
            String description) {
        Claim c = new Claim(owner);
        c.updateDraft(policy, incident, type, "Water damage", description,
                estimate == null ? null : new BigDecimal(estimate));
        return c;
    }

    static Claim submitted(UUID owner, UUID policy, LocalDate incident, IncidentType type, String estimate) {
        Claim c = claim(owner, policy, incident, type, estimate, "x".repeat(80));
        c.submit("CW-2026-" + UUID.randomUUID().toString().substring(0, 6), snapshot("10000.00", "500.00"),
                Instant.now());
        return c;
    }

    @Nested
    class R1RequiredInputs {

        @Test
        void completeClaimHasNoViolations() {
            Claim c = claim(OWNER, POLICY, LocalDate.of(2026, 3, 1), IncidentType.DAMAGE, "0.01", "desc");
            assertThat(ClaimRules.requiredInputs(c, LocalDate.of(2026, 3, 1))).isEmpty();
        }

        @Test
        void missingFieldsAreAllReported() {
            Claim c = new Claim(OWNER);
            List<String> fields = ClaimRules.requiredInputs(c, LocalDate.of(2026, 3, 1)).stream()
                    .map(FieldViolation::field).toList();
            assertThat(fields).containsExactlyInAnyOrder("policyId", "incidentDate", "incidentType", "title",
                    "description", "estimatedLoss");
        }

        @Test
        void estimateMustBeAtLeastOneCentAndDateNotInFuture() {
            Claim c = claim(OWNER, POLICY, LocalDate.of(2026, 3, 2), IncidentType.LOSS, "0.00", "desc");
            List<FieldViolation> v = ClaimRules.requiredInputs(c, LocalDate.of(2026, 3, 1));
            assertThat(v).extracting(FieldViolation::field).containsExactlyInAnyOrder("estimatedLoss",
                    "incidentDate");
            assertThat(v).extracting(FieldViolation::message).contains("Incident date cannot be in the future.");
        }

        @Test
        void blankTextCountsAsMissing() {
            Claim c = claim(OWNER, POLICY, LocalDate.of(2026, 3, 1), IncidentType.LOSS, "10", "   ");
            assertThat(ClaimRules.requiredInputs(c, LocalDate.of(2026, 3, 1))).extracting(FieldViolation::field)
                    .containsExactly("description");
        }
    }

    @Nested
    class R2PolicyActivity {

        @ParameterizedTest
        @CsvSource({"2026-01-01,false", "2026-12-31,false", "2026-06-15,false", "2025-12-31,true",
                "2027-01-01,true"})
        void boundariesAreInclusive(LocalDate incident, boolean flagged) {
            Claim c = claim(OWNER, POLICY, incident, IncidentType.DAMAGE, "100", "d");
            List<FindingDraft> f = ClaimRules.policyActivity(c, snapshot("10000", "500"));
            assertThat(!f.isEmpty()).isEqualTo(flagged);
            if (flagged) {
                assertThat(f.getFirst().rule()).isEqualTo(RuleCode.COVERAGE_REVIEW_REQUIRED);
                assertThat(f.getFirst().severity()).isEqualTo(Severity.WARNING);
            }
        }
    }

    @Nested
    class R3AndR4Amounts {

        @ParameterizedTest
        @CsvSource({"10000.00,false", "10000.01,true", "9999.99,false"})
        void overLimitIsStrictlyGreater(String estimate, boolean flagged) {
            Claim c = claim(OWNER, POLICY, EFFECTIVE, IncidentType.DAMAGE, estimate, "d");
            assertThat(ClaimRules.overLimit(c, snapshot("10000.00", "500.00")).isEmpty()).isEqualTo(!flagged);
        }

        @ParameterizedTest
        @CsvSource({"500.00,true", "499.99,true", "500.01,false", "0.01,true"})
        void belowDeductibleIncludesEquality(String estimate, boolean flagged) {
            Claim c = claim(OWNER, POLICY, EFFECTIVE, IncidentType.DAMAGE, estimate, "d");
            List<FindingDraft> f = ClaimRules.belowDeductible(c, snapshot("10000.00", "500.00"));
            assertThat(!f.isEmpty()).isEqualTo(flagged);
            if (flagged) {
                assertThat(f.getFirst().severity()).isEqualTo(Severity.INFO);
                assertThat(f.getFirst().explanation()).contains("no payable amount");
            }
        }

        @Test
        void bigDecimalScaleDoesNotAffectComparison() {
            Claim c = claim(OWNER, POLICY, EFFECTIVE, IncidentType.DAMAGE, "10000", "d");
            assertThat(ClaimRules.overLimit(c, snapshot("10000.00", "500.00"))).isEmpty();
            assertThat(ClaimRules.money(new BigDecimal("1234.5"))).contains("1,234.50");
        }
    }

    @Nested
    class R5Duplicates {

        @Test
        void matchesSameOwnerPolicyTypeWithinWindowAndTolerance() {
            Claim existing = submitted(OWNER, POLICY, LocalDate.of(2026, 3, 1), IncidentType.DAMAGE, "1000.00");
            Claim candidate = claim(OWNER, POLICY, LocalDate.of(2026, 3, 8), IncidentType.DAMAGE, "1200.00", "d");
            List<FindingDraft> f = ClaimRules.potentialDuplicates(candidate, List.of(existing), SETTINGS);
            assertThat(f).hasSize(1);
            assertThat(f.getFirst().relatedClaimId()).isEqualTo(existing.getId());
            assertThat(f.getFirst().explanation()).contains(existing.getReference()).contains("Not a rejection");
        }

        @Test
        void outsideWindowOrToleranceIsNotADuplicate() {
            Claim existing = submitted(OWNER, POLICY, LocalDate.of(2026, 3, 1), IncidentType.DAMAGE, "1000.00");
            Claim late = claim(OWNER, POLICY, LocalDate.of(2026, 3, 9), IncidentType.DAMAGE, "1000.00", "d");
            Claim pricey = claim(OWNER, POLICY, LocalDate.of(2026, 3, 2), IncidentType.DAMAGE, "1300.00", "d");
            Claim otherType = claim(OWNER, POLICY, LocalDate.of(2026, 3, 2), IncidentType.LOSS, "1000.00", "d");
            assertThat(ClaimRules.potentialDuplicates(late, List.of(existing), SETTINGS)).isEmpty();
            assertThat(ClaimRules.potentialDuplicates(pricey, List.of(existing), SETTINGS)).isEmpty();
            assertThat(ClaimRules.potentialDuplicates(otherType, List.of(existing), SETTINGS)).isEmpty();
        }

        @Test
        void anotherUsersClaimIsNeverLinkedEvenIfPassedIn() {
            Claim strangers = submitted(UUID.randomUUID(), POLICY, LocalDate.of(2026, 3, 1), IncidentType.DAMAGE,
                    "1000.00");
            Claim mine = claim(OWNER, POLICY, LocalDate.of(2026, 3, 1), IncidentType.DAMAGE, "1000.00", "d");
            assertThat(ClaimRules.potentialDuplicates(mine, List.of(strangers), SETTINGS)).isEmpty();
        }

        @Test
        void toleranceIsRelativeToTheLargerAmount() {
            assertThat(ClaimRules.withinTolerance(new BigDecimal("800.00"), new BigDecimal("1000.00"),
                    new BigDecimal("0.20"))).isTrue();
            assertThat(ClaimRules.withinTolerance(new BigDecimal("799.99"), new BigDecimal("1000.00"),
                    new BigDecimal("0.20"))).isFalse();
        }
    }

    @Nested
    class R6Completeness {

        @Test
        void shortDescriptionAndNoEvidenceProduceTwoRecommendations() {
            Claim c = claim(OWNER, POLICY, EFFECTIVE, IncidentType.DAMAGE, "100", "Too short");
            List<FindingDraft> f = ClaimRules.completeness(c, 0, SETTINGS);
            assertThat(f).hasSize(2).allMatch(d -> d.rule() == RuleCode.INFORMATION_RECOMMENDED
                    && d.severity() == Severity.INFO);
        }

        @Test
        void detailedDescriptionWithEvidenceIsComplete() {
            Claim c = claim(OWNER, POLICY, EFFECTIVE, IncidentType.DAMAGE, "100", "y".repeat(60));
            assertThat(ClaimRules.completeness(c, 1, SETTINGS)).isEmpty();
        }
    }
}
