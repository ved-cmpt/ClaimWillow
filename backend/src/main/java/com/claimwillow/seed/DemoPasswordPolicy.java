package com.claimwillow.seed;

import java.util.Optional;

/**
 * Decides whether a demo seed password is acceptable. The demo accounts are publicly listed, so on a
 * hosted instance the password is the only thing protecting them.
 */
public final class DemoPasswordPolicy {

    /** The dev-only password documented in the README; acceptable only for local evaluation. */
    public static final String DOCUMENTED_DEV_PASSWORD = "Willow-Demo-2026!";
    static final int MIN_LENGTH = 12;

    private DemoPasswordPolicy() {
    }

    /** Returns why the password is unacceptable, or empty when it may be used. */
    public static Optional<String> problem(String password, boolean allowDocumentedDevPassword) {
        if (password == null || password.isBlank()) {
            return Optional.of("APP_SEED_DEMO_PASSWORD is not set");
        }
        if (password.length() < MIN_LENGTH) {
            return Optional.of("APP_SEED_DEMO_PASSWORD is shorter than " + MIN_LENGTH + " characters");
        }
        int classes = 0;
        classes += password.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        classes += password.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        classes += password.chars().anyMatch(Character::isDigit) ? 1 : 0;
        classes += password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)) ? 1 : 0;
        if (classes < 3) {
            return Optional.of("APP_SEED_DEMO_PASSWORD must mix at least three of: lowercase, uppercase, digits, "
                    + "symbols");
        }
        if (!allowDocumentedDevPassword && DOCUMENTED_DEV_PASSWORD.equals(password)) {
            return Optional.of("APP_SEED_DEMO_PASSWORD is the publicly documented dev password");
        }
        return Optional.empty();
    }
}
