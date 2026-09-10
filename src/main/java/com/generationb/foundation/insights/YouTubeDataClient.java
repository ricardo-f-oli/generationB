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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * YouTube Data API v3.
 *
 * <p>The easiest of the three platforms by a distance. Everything public is available with a
 * plain API key — no OAuth, no app review, no business verification, no per-creator permission.
 * A channel's statistics and its uploads are simply readable.
 *
 * <h2>Quota, not rate limit</h2>
 *
 * <p>Google meters this in <b>units per day</b>, default 10,000, and the costs are wildly uneven:
 *
 * <ul>
 *   <li>{@code channels.list} — 1 unit
 *   <li>{@code playlistItems.list} — 1 unit
 *   <li>{@code videos.list} — 1 unit
 *   <li>{@code search.list} — <b>100 units</b>
 * </ul>
 *
 * <p>That 100× difference is the whole design. Reading a channel's uploads through its uploads
 * playlist costs 2 units; doing the same thing through search costs 100. Fifty sweeps a day
 * either way is 100 units or 5,000. So this deliberately never uses search where a playlist will
 * do, and the daily counter below exists to make the spend visible rather than a surprise at
 * 4pm when everything starts returning 403.
 */
@Slf4j
@Service
public class YouTubeDataClient {

    private static final int COST_CHEAP = 1;
    private static final int COST_SEARCH = 100;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Units spent today, reset on the first call after midnight Pacific — which is when Google
     * resets, not midnight local. An estimate: Google is the authority, and a 403 quota error is
     * still handled. It exists so the log can say "we spent 9,000 units" before that happens.
     */
    private final AtomicInteger unitsSpentToday = new AtomicInteger();
    private volatile java.time.LocalDate quotaDay = pacificToday();

    @Value("${insights.youtube.base-url:https://www.googleapis.com/youtube/v3}")
    private String baseUrl;

    @Value("${insights.youtube.api-key:}")
    private String apiKey;

    @Value("${insights.youtube.timeout-seconds:20}")
    private int timeoutSeconds;

    /** Google's default. Raised on request through the Cloud console. */
    @Value("${insights.youtube.daily-quota-units:10000}")
    private int dailyQuotaUnits;

    public YouTubeDataClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public int unitsSpentToday() {
        rollQuotaDay();
        return unitsSpentToday.get();
    }

    // =====================================================================

    /**
     * A channel by handle ({@code @name}), by legacy username, or by channel id.
     *
     * <p>All three are in circulation — a creator will give you whichever their profile shows —
     * so this tries the cheap parameter forms rather than falling back to a 100-unit search.
     */
    public Optional<JsonNode> channel(String identifier) {
        String cleaned = trimToNull(identifier);
        if (cleaned == null || !isEnabled()) {
            return Optional.empty();
        }

        // A channel id is the only unambiguous form, and it is recognisable on sight.
        String parameter = cleaned.startsWith("UC") && cleaned.length() == 24 ? "id"
                : cleaned.startsWith("@") ? "forHandle"
                : "forUsername";

        Optional<JsonNode> found = get("channels", Map.of(
                "part", "snippet,statistics,contentDetails",
                parameter, cleaned), COST_CHEAP)
                .map(body -> body.path("items").path(0))
                .filter(item -> !item.isMissingNode() && !item.isEmpty());

        // A bare name might be a handle rather than a legacy username; those were retired, so
        // this second attempt is usually the one that works.
        if (found.isEmpty() && "forUsername".equals(parameter)) {
            return get("channels", Map.of(
                    "part", "snippet,statistics,contentDetails",
                    "forHandle", "@" + cleaned), COST_CHEAP)
                    .map(body -> body.path("items").path(0))
                    .filter(item -> !item.isMissingNode() && !item.isEmpty());
        }
        return found;
    }

    /**
     * A channel's recent uploads.
     *
     * <p>Via the uploads playlist, at 2 units total, rather than {@code search.list} at 100. The
     * uploads playlist id is derivable from the channel id — swap the {@code UC} prefix for
     * {@code UU} — but it is read from {@code contentDetails} here because deriving it is a
     * documented coincidence rather than a guarantee.
     */
    public Optional<JsonNode> recentUploads(String uploadsPlaylistId, int limit) {
        if (trimToNull(uploadsPlaylistId) == null || !isEnabled()) {
            return Optional.empty();
        }
        return get("playlistItems", Map.of(
                "part", "snippet,contentDetails",
                "playlistId", uploadsPlaylistId,
                "maxResults", String.valueOf(Math.clamp(limit, 1, 50))), COST_CHEAP);
    }

    /** View, like and comment counts for videos, up to 50 ids in one call for 1 unit. */
    public Optional<JsonNode> videoStatistics(java.util.List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty() || !isEnabled()) {
            return Optional.empty();
        }
        String ids = String.join(",", videoIds.subList(0, Math.min(videoIds.size(), 50)));
        return get("videos", Map.of("part", "statistics,snippet,contentDetails", "id", ids),
                COST_CHEAP);
    }

    /**
     * Public video search — for brand mentions and competitor monitoring on YouTube.
     *
     * <p><b>100 units.</b> A hundred of these is the entire daily allowance, so callers should
     * treat it the way the Instagram code treats a hashtag: something to spend deliberately, not
     * something to call in a loop.
     */
    public Optional<JsonNode> searchVideos(String query, int limit, java.time.Instant publishedAfter) {
        if (trimToNull(query) == null || !isEnabled()) {
            return Optional.empty();
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("part", "snippet");
        params.put("q", query);
        params.put("type", "video");
        params.put("order", "date");
        params.put("maxResults", String.valueOf(Math.clamp(limit, 1, 50)));
        if (publishedAfter != null) {
            params.put("publishedAfter", publishedAfter.toString());
        }
        return get("search", params, COST_SEARCH);
    }

    // =====================================================================

    private Optional<JsonNode> get(String path, Map<String, String> query, int costUnits) {
        rollQuotaDay();
        if (unitsSpentToday.get() + costUnits > dailyQuotaUnits) {
            log.warn("YouTube quota guard: {} units spent today of {}; declining a {}-unit call "
                    + "to {}", unitsSpentToday.get(), dailyQuotaUnits, costUnits, path);
            return Optional.empty();
        }

        Map<String, String> params = new LinkedHashMap<>(query);
        params.put("key", apiKey);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + path + queryString(params)))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                logError(path, response);
                return Optional.empty();
            }
            // Counted on success only. Google does not charge for a failed request, and counting
            // failures would have the guard shut everything down during an outage.
            unitsSpentToday.addAndGet(costUnits);
            return Optional.of(objectMapper.readTree(response.body()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            // The URL carries the API key.
            log.warn("YouTube {} failed: {}", path, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void logError(String path, HttpResponse<String> response) {
        try {
            JsonNode error = objectMapper.readTree(response.body()).path("error");
            String reason = error.path("errors").path(0).path("reason").asText("");
            String hint = switch (reason) {
                case "quotaExceeded", "dailyLimitExceeded" ->
                        " — the daily quota is spent; it resets at midnight Pacific";
                case "keyInvalid", "badRequest" ->
                        " — check YOUTUBE_API_KEY and that the Data API v3 is enabled for it";
                case "accessNotConfigured" ->
                        " — YouTube Data API v3 is not enabled on this Google Cloud project";
                default -> "";
            };
            log.warn("YouTube {} returned {} ({}){}",
                    path, response.statusCode(), reason.isEmpty() ? "unknown" : reason, hint);
        } catch (Exception e) {
            log.warn("YouTube {} returned {}", path, response.statusCode());
        }
    }

    /** Google's quota resets at midnight Pacific, not local midnight or UTC. */
    private void rollQuotaDay() {
        java.time.LocalDate today = pacificToday();
        if (!today.equals(quotaDay)) {
            synchronized (this) {
                if (!today.equals(quotaDay)) {
                    quotaDay = today;
                    unitsSpentToday.set(0);
                }
            }
        }
    }

    private static java.time.LocalDate pacificToday() {
        return java.time.LocalDate.now(java.time.ZoneId.of("America/Los_Angeles"));
    }

    private static String queryString(Map<String, String> query) {
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
