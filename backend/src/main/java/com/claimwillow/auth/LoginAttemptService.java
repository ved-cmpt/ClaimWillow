package com.claimwillow.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Simple in-memory brute-force protection for the login endpoint: after {@code maxFailures}
 * failed attempts for the same account or client address within the window, further attempts
 * are refused (HTTP 429) until the window passes. Single-instance only; see docs/SECURITY.md.
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(@Value("${app.auth.max-failures:5}") int maxFailures,
            @Value("${app.auth.failure-window-minutes:15}") long windowMinutes) {
        this(maxFailures, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    LoginAttemptService(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** Account keys use the configured limit; client-address keys allow more (shared NAT, tests). */
    public boolean isBlocked(String key) {
        return isBlocked(key, key.startsWith("client:") ? maxFailures * 6 : maxFailures);
    }

    private boolean isBlocked(String key, int limit) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return false;
        }
        synchronized (attempts) {
            prune(attempts);
            return attempts.size() >= limit;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.pollFirst();
        }
    }
}
