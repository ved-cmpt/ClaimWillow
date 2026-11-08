package com.claimwillow.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DemoPasswordPolicyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Short-1a", "alllowercaseletters", "lowercase-and-symbols", "ALLUPPER123456"})
    void weakPasswordsAreRejected(String password) {
        assertThat(DemoPasswordPolicy.problem(password, false)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"E2e-Synthetic-Pass-2026", "maple7River9stone", "correct-horse-Battery"})
    void strongPasswordsAreAccepted(String password) {
        assertThat(DemoPasswordPolicy.problem(password, false)).isEmpty();
    }

    @Test
    void documentedDevPasswordIsOnlyAllowedForLocalEvaluation() {
        assertThat(DemoPasswordPolicy.problem(DemoPasswordPolicy.DOCUMENTED_DEV_PASSWORD, false)).get()
                .asString().contains("publicly documented");
        assertThat(DemoPasswordPolicy.problem(DemoPasswordPolicy.DOCUMENTED_DEV_PASSWORD, true)).isEmpty();
    }
}
