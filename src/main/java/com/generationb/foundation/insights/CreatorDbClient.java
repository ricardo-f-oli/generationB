package com.generationb.foundation.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CreatorDB API v3 — the paid vendor for creator data on Instagram, TikTok and YouTube.
 *
 * <p>Every call is a {@code POST} with a JSON body and the key in an {@code api-key} header. The
 * answer is always the same envelope: {@code success}, {@code data}, {@code errorCode}, and the
 * credits spent and left. This returns {@code data} and nothing else.
 *
 * <h2>Credits, not quota</h2>
 *
 * <p>Every call spends credits, there is no sandbox, and an exhausted balance is a hard stop for
 * the month. The costs that matter here (full bundle, per call):
 *
 * <ul>
 *   <li>{@code profile} — 2
 *   <li>{@code performance} — 1.5 to 3
 *   <li>{@code content-detail} — 3
 *   <li>{@code audience} — <b>10</b>
 *   <li>{@code search} — 1 per 10 filters, without enrichment
 * </ul>
 *
 * <p>A lookup that finds nothing is free; a search that finds nothing is not.
 *
 * <p>Each response reports the balance left, which is kept here. Once it falls to
 * {@code insights.creatordb.min-credits} the client stops calling, so a runaway batch cannot
 * spend the last of the month's credits on a background job. The figure lives in memory and is
 * re-learned from the first response after a restart. CreatorDB is the authority either way: a
 * {@code 402} is logged with what it means and the call returns empty.
 */
@Slf4j
@Service
public class CreatorDbClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${insights.creatordb.base-url:https://apiv3.creatordb.app}")
    private String baseUrl;

    @Value("${insights.creatordb.api-key:}")
    private String apiKey;

    @Value("${insights.creatordb.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${insights.creatordb.min-credits:100}")
    private double minCredits;

    /** The balance from the last response, or null before the first. */
    private volatile Double creditsAvailable;

    /** So a spent balance is logged once rather than on every skipped call. */
    private final AtomicBoolean reserveWarned = new AtomicBoolean();

    public CreatorDbClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** The balance CreatorDB last reported, if any call has been made since startup. */
    public Optional<Double> creditsAvailable() {
        return Optional.ofNullable(creditsAvailable);
    }

    /**
     * The outcome of one call: the {@code data} when it succeeded, and otherwise the
     * {@code errorCode} — {@code NOT_FOUND} when CreatorDB has not indexed the creator,
     * {@link #RESERVE} when the local reserve stopped the call, {@link #FAILED} for a transport error.
     */
    public record Result(Optional<JsonNode> data, String errorCode) {

        public boolean notIndexed() {
            return NOT_FOUND.equals(errorCode);
        }
    }

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String RESERVE = "CREDIT_RESERVE";
    public static final String FAILED = "FAILED";
    public static final String DISABLED = "DISABLED";

    /**
     * One call. {@code path} is relative, e.g. {@code instagram/profile}.
     *
     * @return the response's {@code data}, or empty when CreatorDB has nothing, refused, or the
     *         reserve has been reached
     */
    public Optional<JsonNode> post(String path, Map<String, Object> body) {
        return call(path, body).data();
    }

    /** As {@link #post}, keeping the reason when there is no data. */
    public Result call(String path, Map<String, Object> body) {
        if (!isEnabled()) {
            return new Result(Optional.empty(), DISABLED);
        }
        Double balance = creditsAvailable;
        // CreatorDB reports -1 for an unlimited quota, which must not read as "spent".
        if (balance != null && balance >= 0 && balance <= minCredits) {
            if (reserveWarned.compareAndSet(false, true)) {
                log.warn("CreatorDB has {} credits left, at or below the reserve of {}; calls are "
                        + "paused until the balance is topped up (or the app restarts)",
                        balance, minCredits);
            }
            return new Result(Optional.empty(), RESERVE);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + path))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("api-key", apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode envelope = readTree(response.body());
            if (envelope.hasNonNull("creditsAvailable")) {
                creditsAvailable = envelope.path("creditsAvailable").asDouble();
                reserveWarned.set(false);
            }

            if (response.statusCode() >= 300 || !envelope.path("success").asBoolean(false)) {
                logError(path, response.statusCode(), envelope);
                String code = envelope.path("errorCode").asText("");
                if (code.isEmpty()) {
                    code = response.statusCode() == 404 ? NOT_FOUND : FAILED;
                }
                return new Result(Optional.empty(), code);
            }

            log.debug("CreatorDB {} used {} credit(s), {} left", path,
                    envelope.path("creditsUsed").asDouble(0), creditsAvailable);
            JsonNode data = envelope.path("data");
            return new Result(data.isMissingNode() || data.isNull() ? Optional.empty() : Optional.of(data), "");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Optional.empty(), FAILED);
        } catch (Exception e) {
            log.warn("CreatorDB {} failed: {}", path, e.getClass().getSimpleName());
            return new Result(Optional.empty(), FAILED);
        }
    }

    // =====================================================================

    private void logError(String path, int status, JsonNode envelope) {
        String code = envelope.path("errorCode").asText("");
        if ("NOT_FOUND".equals(code) || status == 404) {
            // Not a fault: CreatorDB has not indexed this creator. Free, and common for small ones.
            log.info("CreatorDB has no record for {} ({})", path, code.isEmpty() ? status : code);
            return;
        }
        if (status == 402) {
            creditsAvailable = envelope.path("details").path("creditsAvailable").asDouble(0);
        }
        String hint = switch (code) {
            case "UNAUTHORIZED" -> " — check CREATORDB_API_KEY";
            case "INSUFFICIENT_CREDITS" -> " — the credit balance is spent; top up or wait for the plan refresh";
            case "KEY_SPEND_THRESHOLD_EXCEEDED" -> " — this key has hit the spend limit set on it in the CreatorDB dashboard";
            case "FRACTIONAL_CALLS_NOT_ENABLED" -> " — a request asked for a field subset, which this key does not allow";
            case "RATE_LIMIT_EXCEEDED" -> " — too many calls a minute; try again shortly";
            case "VALIDATION_ERROR" -> " — " + envelope.path("details").toString();
            default -> "";
        };
        log.warn("CreatorDB {} returned {} ({}){} [trace {}]", path, status,
                code.isEmpty() ? "unknown" : code, hint, envelope.path("traceId").asText("-"));
    }

    private JsonNode readTree(String body) {
        try {
            return body == null || body.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(body);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
