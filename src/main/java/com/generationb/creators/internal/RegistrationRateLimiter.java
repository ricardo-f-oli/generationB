package com.generationb.creators.internal;

import com.generationb.foundation.ApiException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Q-B15: the public registration endpoint had no rate limit and no captcha.
 *
 * <p>A captcha needs a third-party script and a new dependency (Q-Z2 rules that out for now), so
 * this is an in-memory sliding window per IP — enough to stop casual scripted abuse of a demo
 * deployment. It is per-instance, which is fine while we run one instance on Render; a shared
 * store is noted as follow-up work in QUESTIONS.md.
 */
@Component
public class RegistrationRateLimiter {

    private static final int MAX_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_TRACKED_IPS = 10_000;

    private final Map<String, Deque<Instant>> attemptsByIp = new ConcurrentHashMap<>();

    public void checkAndRecord(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        Instant cutoff = Instant.now().minus(WINDOW);

        if (attemptsByIp.size() > MAX_TRACKED_IPS) {
            attemptsByIp.clear();
        }

        Deque<Instant> attempts = attemptsByIp.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= MAX_PER_WINDOW) {
                throw ApiException.tooManyRequests(
                        "Too many registrations from this network. Please try again later.");
            }
            attempts.addLast(Instant.now());
        }
    }
}
