package com.claimwillow.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DemoPasswordPolicyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "        ", "demo123", "short"})
    void missingOrShortPasswordsAreRejected(String password) {
        assertThat(DemoPasswordPolicy.problem(password)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo1234", "abcdefgh", "E2e-Synthetic-Pass-2026"})
    void passwordsWithAtLeastEightCharactersAreAccepted(String password) {
        assertThat(DemoPasswordPolicy.problem(password)).isEmpty();
    }

    @Test
    void thePublishedDemoPasswordIsAccepted() {
        assertThat(DemoPasswordPolicy.PUBLIC_DEMO_PASSWORD).isEqualTo("demo1234");
        assertThat(DemoPasswordPolicy.problem(DemoPasswordPolicy.PUBLIC_DEMO_PASSWORD)).isEmpty();
    }

    @Test
    void shortPasswordExplainsTheMinimum() {
        assertThat(DemoPasswordPolicy.problem("demo")).get().asString().contains("8 characters");
    }
}
