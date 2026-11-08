package com.claimwillow.claim;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

/**
 * Public claim references like {@code CW-2026-7KQ4MX}: year plus six random characters from an
 * unambiguous alphabet. They never expose database sequence values.
 */
@Component
public class ReferenceGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public ReferenceGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder sb = new StringBuilder("CW-").append(LocalDate.now(clock).getYear()).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
