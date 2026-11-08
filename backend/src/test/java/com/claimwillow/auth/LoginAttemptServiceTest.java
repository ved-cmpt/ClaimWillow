package com.claimwillow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void blocksAccountAfterLimitAndUnblocksAfterWindow() {
        LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(15), clock);
        String key = "account:a@example.com";
        for (int i = 0; i < 3; i++) {
            assertThat(service.isBlocked(key)).isFalse();
            service.recordFailure(key);
        }
        assertThat(service.isBlocked(key)).isTrue();
        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertThat(service.isBlocked(key)).isFalse();
    }

    @Test
    void successClearsAccountFailures() {
        LoginAttemptService service = new LoginAttemptService(2, Duration.ofMinutes(15), clock);
        String key = "account:b@example.com";
        service.recordFailure(key);
        service.recordSuccess(key);
        service.recordFailure(key);
        assertThat(service.isBlocked(key)).isFalse();
    }

    @Test
    void clientAddressKeysTolerateMoreFailures() {
        LoginAttemptService service = new LoginAttemptService(2, Duration.ofMinutes(15), clock);
        String key = "client:10.0.0.1";
        for (int i = 0; i < 11; i++) {
            service.recordFailure(key);
        }
        assertThat(service.isBlocked(key)).isFalse();
        service.recordFailure(key);
        assertThat(service.isBlocked(key)).isTrue();
    }
}
