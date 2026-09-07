package com.generationb.foundation.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Transport for the Modash creator-data API — the vendor the gap analysis lists as blocking
 * requirements #10, #11, #25 and #26.
 *
 * <p>Three things this class exists to get right, none of which belong in a mapper:
 *
 * <ul>
 *   <li><b>The budget.</b> The account is metered in credits, and a report is a whole credit. A
 *       loop over a shortlist can empty the allowance in one click, so every metered call checks
 *       the remaining balance against a reserve first and declines rather than overspending.
 *   <li><b>The rate limit.</b> Modash allows 2 requests a second and answers 429 above it. Calls
 *       are spaced by a minimum interval and a 429 is retried with backoff, so a bulk enrichment
 *       is slow rather than half-failed.
 *   <li><b>Silence on failure.</b> Like {@code GroqAiClient}, a failed call returns empty and
 *       logs the status only. Response bodies carry creator names and contact details.
 * </ul>
 *
 * <p>It returns raw {@link JsonNode} on purpose. Modash's payloads are wide and change shape
 * between plans; binding them to records here would mean a deploy every time the vendor adds a
 * field. The mapping to our own model lives in the creators module.
 */
@Slf4j
@Service
public class ModashClient {

    /**
     * What a call spends. The two allowances are metered separately by Modash and reported
     * separately by {@code /user/info}, so the guard has to know which one a path draws on.
     */
    public enum Meter {
        /** Discovery credits: search, reports, collaborations. */
        DISCOVERY,
        /** Raw requests: the post feeds behind auto-clipping. */
        RAW,
        /** Costs nothing — handle lookup, dictionaries, {@code /user/info}. */
        FREE
    }

    /** What the account has left. Refreshed from {@code /user/info}, which is itself free. */
    public record Budget(double credits, int rawRequests, Instant checkedAt) {

        public boolean canSpend(Meter meter, double amount, double creditReserve, int rawReserve) {
            return switch (meter) {
                case DISCOVERY -> credits - amount >= creditReserve;
                case RAW -> rawRequests - amount >= rawReserve;
                case FREE -> true;
            };
        }
    }

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** Guards the spacing between calls. A virtual thread parks here rather than pinning. */
    private final ReentrantLock throttle = new ReentrantLock();
    private long lastRequestNanos = 0L;

    private final AtomicReference<Budget> budget = new AtomicReference<>();

    @Value("${insights.modash.base-url:https://api.modash.io/v1}")
    private String baseUrl;

    @Value("${insights.modash.api-key:}")
    private String apiKey;

    @Value("${insights.modash.timeout-seconds:25}")
    private int timeoutSeconds;

    /** 2 requests/second is the documented limit; 550ms leaves a little headroom. */
    @Value("${insights.modash.min-request-interval-ms:550}")
    private long minRequestIntervalMs;

    @Value("${insights.modash.max-retries:2}")
    private int maxRetries;

    /**
     * Never spend the account down to nothing. Leaving a float means a report someone is waiting
     * on in a meeting still runs after a bulk job has been careless.
     */
    @Value("${insights.modash.credit-reserve:2}")
    private double creditReserve;

    @Value("${insights.modash.raw-reserve:2}")
    private int rawReserve;

    @Value("${insights.modash.budget-ttl-seconds:120}")
    private long budgetTtlSeconds;

    public ModashClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** No key means the mock provider stays in place, exactly as no AI key means a local draft. */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    // =====================================================================
    // Calls
    // =====================================================================

    public Optional<JsonNode> get(String path, Map<String, String> query, Meter meter, double cost) {
        return send("GET", path + queryString(query), null, meter, cost);
    }

    public Optional<JsonNode> post(String path, Object body, Meter meter, double cost) {
        return send("POST", path, body, meter, cost);
    }

    /**
     * The account balance. Free to call, so the budget guard can be honest rather than guessing
     * from a counter this process happens to have kept.
     *
     * @param maxAge accept a cached reading no older than this; {@link Duration#ZERO} forces a refresh
     */
    public Optional<Budget> budget(Duration maxAge) {
        Budget cached = budget.get();
        if (cached != null && !cached.checkedAt().isBefore(Instant.now().minus(maxAge))) {
            return Optional.of(cached);
        }
        return refreshBudget();
    }

    /** The reading the guard uses: cached for {@code budget-ttl-seconds}. */
    public Optional<Budget> budget() {
        return budget(Duration.ofSeconds(budgetTtlSeconds));
    }

    private Optional<Budget> refreshBudget() {
        Optional<JsonNode> body = send("GET", "/user/info", null, Meter.FREE, 0);
        if (body.isEmpty()) {
            return Optional.empty();
        }
        JsonNode billing = body.get().path("billing");
        Budget fresh = new Budget(
                billing.path("credits").asDouble(0),
                billing.path("rawRequests").asInt(0),
                Instant.now());
        budget.set(fresh);
        return Optional.of(fresh);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private Optional<JsonNode> send(String method, String path, Object body, Meter meter, double cost) {
        if (!isEnabled()) {
            log.debug("No Modash key configured; the caller will use its own fallback");
            return Optional.empty();
        }
        if (!affordable(meter, cost)) {
            return Optional.empty();
        }

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            throttleNow();
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(timeoutSeconds));

                if (body == null) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(body)));
                }

                HttpResponse<String> response =
                        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 429 || status >= 500) {
                    if (attempt < maxRetries) {
                        backoff(attempt, response);
                        continue;
                    }
                    // A 5xx costs nothing per Modash's billing rules, so the cached balance stands.
                    log.warn("Modash {} returned {} after {} attempt(s)", path, status, attempt + 1);
                    return Optional.empty();
                }
                if (status >= 300) {
                    // The body echoes the query, which can name a creator — status only.
                    log.warn("Modash {} returned {}", path, status);
                    return Optional.empty();
                }

                JsonNode json = objectMapper.readTree(response.body());
                if (json.path("error").isBoolean() && json.path("error").asBoolean()) {
                    log.warn("Modash {} reported an application error (code {})",
                            path, json.path("code").asText("unknown"));
                    return Optional.empty();
                }

                spend(meter, cost);
                return Optional.of(json);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception e) {
                // Class name only: the message can contain the URL, and the URL contains a handle.
                log.warn("Modash {} failed: {}", path, e.getClass().getSimpleName());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Refuses a metered call that would take the account below its reserve.
     *
     * <p>When the balance cannot be read the call is allowed through: an unreachable
     * {@code /user/info} should not take the whole integration down, and the vendor enforces the
     * real limit anyway.
     */
    private boolean affordable(Meter meter, double cost) {
        if (meter == Meter.FREE || cost <= 0) {
            return true;
        }
        Optional<Budget> current = budget();
        if (current.isEmpty()) {
            return true;
        }
        if (current.get().canSpend(meter, cost, creditReserve, rawReserve)) {
            return true;
        }
        log.warn("Modash call declined: {} balance is {} and the reserve is {}. Top up the account.",
                meter, meter == Meter.DISCOVERY ? current.get().credits() : current.get().rawRequests(),
                meter == Meter.DISCOVERY ? creditReserve : rawReserve);
        return false;
    }

    /**
     * Decrements the cached balance so a burst of calls inside one TTL window still sees the
     * cost of the ones before it. The next refresh replaces the estimate with the truth.
     */
    private void spend(Meter meter, double cost) {
        if (meter == Meter.FREE || cost <= 0) {
            return;
        }
        budget.updateAndGet(current -> current == null ? null : switch (meter) {
            case DISCOVERY -> new Budget(
                    Math.max(0, current.credits() - cost), current.rawRequests(), current.checkedAt());
            case RAW -> new Budget(
                    current.credits(), Math.max(0, current.rawRequests() - (int) Math.ceil(cost)),
                    current.checkedAt());
            case FREE -> current;
        });
    }

    /** Spaces calls to stay inside the documented 2 requests per second. */
    private void throttleNow() {
        throttle.lock();
        try {
            long waitMs = minRequestIntervalMs
                    - Duration.ofNanos(System.nanoTime() - lastRequestNanos).toMillis();
            if (lastRequestNanos != 0 && waitMs > 0) {
                Thread.sleep(waitMs);
            }
            lastRequestNanos = System.nanoTime();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            throttle.unlock();
        }
    }

    /** Honours {@code Retry-After} when Modash sends one, otherwise backs off exponentially. */
    private void backoff(int attempt, HttpResponse<String> response) throws InterruptedException {
        long millis = response.headers().firstValue("retry-after")
                .map(value -> {
                    try {
                        return Long.parseLong(value.trim()) * 1000;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(minRequestIntervalMs * (1L << attempt));
        Thread.sleep(Math.min(millis, 10_000));
    }

    private static String queryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        query.forEach((key, value) -> {
            if (value == null || value.isBlank()) {
                return;
            }
            out.append(out.isEmpty() ? '?' : '&')
                    .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return out.toString();
    }
}
