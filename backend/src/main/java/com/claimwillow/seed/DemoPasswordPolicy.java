package com.claimwillow.seed;

import java.util.Optional;

/**
 * Decides whether the shared password for the seeded demo accounts is acceptable.
 *
 * <p>The demo accounts hold synthetic data only and their password is published on purpose so
 * visitors can sign in, so this check is deliberately lighter than a real password policy: the
 * password must be set and have at least {@value #MIN_LENGTH} characters. It applies to the seeded
 * demo accounts only. BCrypt hashing, login rate limiting, CSRF protection and role checks still
 * apply to every account. See docs/SECURITY.md.
 */
public final class DemoPasswordPolicy {

    /** The published demo password; also the local and Docker Compose default. */
    public static final String PUBLIC_DEMO_PASSWORD = "demo1234";
    static final int MIN_LENGTH = 8;

    private DemoPasswordPolicy() {
    }

    /** Returns why the password is unacceptable, or empty when it may be used. */
    public static Optional<String> problem(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of("APP_SEED_DEMO_PASSWORD is not set");
        }
        if (password.length() < MIN_LENGTH) {
            return Optional.of("APP_SEED_DEMO_PASSWORD is shorter than " + MIN_LENGTH + " characters");
        }
        return Optional.empty();
    }
}
