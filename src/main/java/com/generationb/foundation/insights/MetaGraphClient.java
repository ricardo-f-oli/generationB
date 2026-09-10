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
import java.util.concurrent.locks.ReentrantLock;

/**
 * Meta's Graph API, for Instagram.
 *
 * <p>Replaces the paid creator-data vendor for the two things Meta will actually answer without
 * the creator's involvement:
 *
 * <ul>
 *   <li><b>Business Discovery</b> — any public professional account's follower count, bio and
 *       recent posts, looked up by handle. No permission from that creator required. This is what
 *       covers auto-clipping (#10).
 *   <li><b>Hashtag Search</b> — recent public posts carrying a hashtag, which covers brand
 *       mention discovery (#11) and competitor monitoring (#25).
 * </ul>
 *
 * <p>And one thing it will only answer <em>with</em> the creator's involvement:
 *
 * <ul>
 *   <li><b>Insights</b> — audience age, gender and location, for an account that has connected
 *       to our app. Better data than a vendor's estimate, because it is Instagram's own, but it
 *       needs the creator to click through an OAuth flow first (#26).
 * </ul>
 *
 * <h2>The two limits that shape everything</h2>
 *
 * <p><b>Hashtags: 30 unique tags per rolling 7 days, per app.</b> Not per brand — per app. With
 * several client brands each monitoring their own tags plus competitors, that budget is the real
 * constraint on mention discovery, which is why {@link HashtagBudget} exists and why a sweep
 * checks it before spending.
 *
 * <p><b>Business Discovery: 200 calls per hour per account looked up.</b> Generous for our
 * pattern, which is a handful of creators at a time.
 *
 * <h2>What it cannot do</h2>
 *
 * <p>There is no creator search. Meta indexes hashtags, not people, so nothing here replaces
 * semantic discovery — you can find posts about a topic, not creators matching a description.
 */
@Slf4j
@Service
public class MetaGraphClient {

    /** Business Discovery reaches a target account only through our own IG account's node. */
    private static final String BUSINESS_DISCOVERY_FIELDS =
            "id,username,name,biography,website,followers_count,follows_count,media_count,"
                    + "profile_picture_url";

    /** Post fields. Instagram gives like and comment counts; views only on some media types. */
    private static final String MEDIA_FIELDS =
            "id,caption,like_count,comments_count,media_type,media_product_type,media_url,"
                    + "permalink,timestamp,thumbnail_url";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final ReentrantLock throttle = new ReentrantLock();
    private long lastRequestNanos = 0L;

    @Value("${insights.meta.base-url:https://graph.facebook.com}")
    private String baseUrl;

    /**
     * Pinned rather than floating. Meta deprecates a version roughly every two years and changes
     * field shapes between them; discovering that from a production error is worse than
     * discovering it from a calendar reminder.
     */
    @Value("${insights.meta.api-version:v21.0}")
    private String apiVersion;

    /**
     * A long-lived Page access token for the agency's own Facebook Page, which is linked to the
     * Instagram Business account named below. Business Discovery and Hashtag Search are both
     * made <em>as</em> that account.
     */
    @Value("${insights.meta.access-token:}")
    private String accessToken;

    /** The agency's own Instagram Business account id. Every lookup is routed through it. */
    @Value("${insights.meta.ig-user-id:}")
    private String igUserId;

    @Value("${insights.meta.timeout-seconds:20}")
    private int timeoutSeconds;

    @Value("${insights.meta.min-request-interval-ms:120}")
    private long minRequestIntervalMs;

    public MetaGraphClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Both are needed: the token authenticates, the account id is what the call is made as. */
    public boolean isEnabled() {
        return notBlank(accessToken) && notBlank(igUserId);
    }

    // =====================================================================
    // Business Discovery — no permission needed from the target
    // =====================================================================

    /**
     * A public professional account, looked up by handle.
     *
     * @return empty when the handle does not exist, is a personal account, or Meta declines.
     *         Those are not distinguishable from the response, which is why the caller reports
     *         "no data" rather than guessing which.
     */
    public Optional<JsonNode> businessDiscovery(String handle) {
        String cleaned = handleOf(handle);
        if (cleaned == null || !isEnabled()) {
            return Optional.empty();
        }
        // The nested-field syntax is Meta's own and is not URL-encoded as a whole — encoding the
        // parentheses breaks it.
        String fields = "business_discovery.username(" + cleaned + "){"
                + BUSINESS_DISCOVERY_FIELDS + "}";

        return get(igUserId, Map.of("fields", fields))
                .map(body -> body.path("business_discovery"))
                .filter(node -> !node.isMissingNode() && !node.isEmpty());
    }

    /**
     * A public professional account's recent posts.
     *
     * @param limit Meta caps a page at 100; asking for more silently returns 100.
     */
    public Optional<JsonNode> businessDiscoveryMedia(String handle, int limit) {
        String cleaned = handleOf(handle);
        if (cleaned == null || !isEnabled()) {
            return Optional.empty();
        }
        String fields = "business_discovery.username(" + cleaned + "){username,followers_count,"
                + "media.limit(" + Math.clamp(limit, 1, 100) + "){" + MEDIA_FIELDS + "}}";

        return get(igUserId, Map.of("fields", fields))
                .map(body -> body.path("business_discovery"))
                .filter(node -> !node.isMissingNode() && !node.isEmpty());
    }

    // =====================================================================
    // Hashtag search — the metered one
    // =====================================================================

    /**
     * Resolves a hashtag to its Graph node id.
     *
     * <p>Each <em>distinct</em> hashtag resolved counts against the 30-per-7-days allowance, so
     * the caller must check {@link HashtagBudget} first. Resolving the same tag again inside the
     * window is free, which is why ids are worth caching.
     */
    public Optional<String> hashtagId(String tag) {
        String cleaned = tagOf(tag);
        if (cleaned == null || !isEnabled()) {
            return Optional.empty();
        }
        return get("ig_hashtag_search", Map.of("user_id", igUserId, "q", cleaned))
                .map(body -> body.path("data").path(0).path("id"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    /**
     * Recent public posts carrying a hashtag.
     *
     * @param top {@code true} for Instagram's own ranking of the best-performing posts,
     *            {@code false} for the most recent. Recent is what coverage monitoring wants —
     *            top surfaces the same handful of viral posts every time you look.
     */
    public Optional<JsonNode> hashtagMedia(String hashtagId, boolean top, int limit) {
        if (!notBlank(hashtagId) || !isEnabled()) {
            return Optional.empty();
        }
        // Hashtag media exposes a narrower field set than a creator's own media: no like_count on
        // recent_media, and never a username you can attribute to.
        String edge = top ? "top_media" : "recent_media";
        return get(hashtagId + "/" + edge, Map.of(
                "user_id", igUserId,
                "fields", "id,caption,media_type,media_url,permalink,timestamp,children{media_url}",
                "limit", String.valueOf(Math.clamp(limit, 1, 50))));
    }

    // =====================================================================
    // Insights — needs the creator connected
    // =====================================================================

    /**
     * Audience demographics for an account that has connected to our app.
     *
     * <p>Instagram's own figures rather than a vendor's model, which makes them the better data —
     * but only reachable with the creator's consent, and only for Business or Creator accounts.
     * Meta also suppresses breakdowns for audiences below its reporting minimum, so a small
     * account legitimately returns nothing.
     *
     * @param connectedToken that creator's own access token, not ours
     */
    public Optional<JsonNode> audienceDemographics(String igAccountId, String connectedToken,
                                                    String breakdown) {
        if (!notBlank(igAccountId) || !notBlank(connectedToken)) {
            return Optional.empty();
        }
        return request(igAccountId + "/insights", Map.of(
                "metric", "follower_demographics",
                "period", "lifetime",
                "timeframe", "this_month",
                "breakdown", breakdown,
                "metric_type", "total_value"), connectedToken);
    }

    /** A connected account's own profile, to confirm the token works and read follower count. */
    public Optional<JsonNode> connectedProfile(String igAccountId, String connectedToken) {
        return request(igAccountId, Map.of(
                "fields", "id,username,name,biography,followers_count,follows_count,media_count,"
                        + "profile_picture_url"), connectedToken);
    }

    // =====================================================================
    // Transport
    // =====================================================================

    private Optional<JsonNode> get(String path, Map<String, String> query) {
        return request(path, query, accessToken);
    }

    private Optional<JsonNode> request(String path, Map<String, String> query, String token) {
        if (!notBlank(token)) {
            return Optional.empty();
        }
        throttleNow();

        Map<String, String> params = new LinkedHashMap<>(query);
        params.put("access_token", token);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + apiVersion + "/" + path + queryString(params)))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                logGraphError(path, response);
                return Optional.empty();
            }

            JsonNode body = objectMapper.readTree(response.body());
            if (body.has("error")) {
                logGraphError(path, response);
                return Optional.empty();
            }
            return Optional.of(body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            // Class name only: the URL carries an access token and a creator handle.
            log.warn("Meta Graph {} failed: {}", path, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Meta's errors are genuinely informative, unlike most, and the codes are what you need to
     * tell "this handle is not a professional account" from "your token expired".
     */
    private void logGraphError(String path, HttpResponse<String> response) {
        try {
            JsonNode error = objectMapper.readTree(response.body()).path("error");
            int code = error.path("code").asInt();
            int subcode = error.path("error_subcode").asInt();
            String type = error.path("type").asText("");

            String hint = switch (code) {
                case 190 -> "the access token is invalid or has expired — re-issue the Page token";
                case 4, 17, 32 -> "application rate limit reached; back off";
                case 10, 200, 803 -> "missing permission, or the target is not a professional "
                        + "account (Business Discovery only sees Business and Creator accounts)";
                case 24 -> "hashtag allowance exhausted: 30 unique tags per rolling 7 days";
                default -> "";
            };
            log.warn("Meta Graph {} returned {} (code {}{}, {}){}",
                    path, response.statusCode(), code,
                    subcode > 0 ? "/" + subcode : "", type,
                    hint.isEmpty() ? "" : " — " + hint);
        } catch (Exception e) {
            log.warn("Meta Graph {} returned {}", path, response.statusCode());
        }
    }

    /** Meta's per-app rate limits are generous but real; spacing calls keeps us clear of them. */
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

    private static String handleOf(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceFirst("^@", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String tagOf(String raw) {
        if (raw == null) {
            return null;
        }
        // Meta rejects a leading # and anything non-alphanumeric.
        String cleaned = raw.trim().replaceFirst("^#", "").replaceAll("[^\\p{L}\\p{N}_]", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
