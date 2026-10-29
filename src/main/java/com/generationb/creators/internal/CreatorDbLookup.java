package com.generationb.creators.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.generationb.foundation.insights.CreatorDbClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CreatorDB's answers, mapped onto the shapes the rest of the app already reads: the coverage
 * post map, the demographics map, the discovery result, and a profile snapshot for refresh.
 *
 * <p>{@link CreatorDbClient} is transport only; everything that knows what a CreatorDB field
 * means is here, so the provider, the profile refresh, discovery and the audience report agree.
 *
 * <h2>Units</h2>
 *
 * <p>CreatorDB gives every rate and share as a ratio ({@code 0.0493}); the app stores
 * percentages ({@code 4.93}). Countries come as ISO alpha-3 ({@code GBR}) and are shown as
 * alpha-2 ({@code GB}), the form Meta used and the stored figures already have.
 *
 * <h2>Engagement rate</h2>
 *
 * <p>Kept to the definitions the profile screen states: on Instagram (likes + comments) ÷
 * followers, which is CreatorDB's image-post rate; on YouTube (likes + comments) ÷ views, which
 * is theirs for long-form videos. TikTok is CreatorDB's own (likes + comments + shares) ÷ views.
 */
@Slf4j
@Component
public class CreatorDbLookup {

    public static final String INSTAGRAM = "INSTAGRAM";
    public static final String TIKTOK = "TIKTOK";
    public static final String YOUTUBE = "YOUTUBE";
    public static final List<String> PLATFORMS = List.of(INSTAGRAM, TIKTOK, YOUTUBE);

    /** What CreatorDB accepts as a handle on any of the three platforms. */
    private static final Pattern VALID_HANDLE = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    private static final Map<String, String> ALPHA3_TO_ALPHA2 = Arrays.stream(Locale.getISOCountries())
            .collect(Collectors.toMap(c -> Locale.of("", c).getISO3Country(), c -> c, (a, b) -> a));

    /** Where a creator can be looked up, primary platform first. */
    public record Target(String platform, String handle) {
    }

    /** What a profile refresh writes. Null fields are ones CreatorDB did not answer. */
    public record Snapshot(Long followers, String bio, BigDecimal erPercentage) {
    }

    /**
     * A profile lookup's outcome. {@code notIndexed} distinguishes "CreatorDB has never heard of
     * this creator" — worth submitting — from a failure, which is not.
     */
    public record SnapshotResult(Optional<Snapshot> snapshot, boolean notIndexed) {
    }

    /** The full audience breakdown, as stored and as the report screen shows it. */
    public record AudienceBreakdown(List<Share> countries, List<Share> ageBands,
                                    BigDecimal femalePct, BigDecimal malePct, Integer averageAge) {

        public boolean isEmpty() {
            return countries.isEmpty() && ageBands.isEmpty() && femalePct == null && averageAge == null;
        }
    }

    /** One slice of an audience: a country (alpha-2) or an age range, with its percentage. */
    public record Share(String key, BigDecimal pct) {
    }

    /** What the discovery screen can filter on. Every field is optional. */
    public record DiscoveryQuery(String platform, String keywords, String country,
                                 Long minFollowers, Long maxFollowers, int offset) {
    }

    public record DiscoveredCreator(String platform, String handle, String name, Long followers,
                                    String avatarUrl, String channelId) {
    }

    public record DiscoveryPage(List<DiscoveredCreator> creators, long totalResults,
                                boolean hasNextPage, Integer nextOffset) {

        static DiscoveryPage empty() {
            return new DiscoveryPage(List.of(), 0, false, null);
        }
    }

    private record CachedPosts(List<Map<String, Object>> posts, Instant at) {
    }

    private final CreatorDbClient client;

    /** Clipping a campaign and then one of its creators asks for the same posts twice. */
    private final Map<String, CachedPosts> postCache = new ConcurrentHashMap<>();

    @Value("${insights.creatordb.post-cache-minutes:60}")
    private int postCacheMinutes;

    @Value("${insights.creatordb.search-page-size:25}")
    private int searchPageSize;

    public CreatorDbLookup(CreatorDbClient client) {
        this.client = client;
    }

    public boolean isEnabled() {
        return client.isEnabled();
    }

    // =====================================================================
    // Which handles to look up
    // =====================================================================

    /**
     * The creator's handles, primary platform first.
     *
     * <p>The main handle belongs to the primary platform. TikTok and YouTube also have their own
     * columns; Instagram does not, so an Instagram lookup only happens for a creator whose
     * primary platform is Instagram — looking a TikTok handle up there would find a stranger.
     */
    public static List<Target> targetsOf(Creator creator) {
        String primary = creator.getPrimaryPlatform() == null || creator.getPrimaryPlatform().isBlank()
                ? INSTAGRAM : creator.getPrimaryPlatform().trim().toUpperCase();

        Map<String, String> handles = new LinkedHashMap<>();
        putIfValid(handles, primary, creator.getHandle());
        putIfValid(handles, TIKTOK, creator.getTiktokHandle());
        putIfValid(handles, YOUTUBE, creator.getYoutubeHandle());

        return handles.entrySet().stream()
                .filter(e -> PLATFORMS.contains(e.getKey()))
                .map(e -> new Target(e.getKey(), e.getValue()))
                .toList();
    }

    private static void putIfValid(Map<String, String> handles, String platform, String raw) {
        String handle = handleOf(raw);
        if (handle != null) {
            handles.putIfAbsent(platform, handle);
        }
    }

    static String handleOf(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceFirst("^@", "");
        return VALID_HANDLE.matcher(cleaned).matches() ? cleaned : null;
    }

    // =====================================================================
    // Profile refresh: profile (2 credits) + performance (1.5–3)
    // =====================================================================

    public Optional<Snapshot> snapshot(Target target) {
        return lookupSnapshot(target).snapshot();
    }

    public SnapshotResult lookupSnapshot(Target target) {
        CreatorDbClient.Result profile = client.call(path(target, "profile"), identify(target));
        if (profile.data().isEmpty()) {
            return new SnapshotResult(Optional.empty(), profile.notIndexed());
        }
        JsonNode p = profile.data().get();
        String followersField = YOUTUBE.equals(target.platform()) ? "totalSubscribers" : "totalFollowers";
        Long followers = p.hasNonNull(followersField) ? p.path(followersField).asLong() : null;
        String bio = p.hasNonNull("bio") ? p.path("bio").asText() : null;

        BigDecimal er = client.post(path(target, "performance"), identify(target))
                .flatMap(perf -> engagementRatio(target.platform(), perf))
                .map(CreatorDbLookup::percent)
                .orElse(null);

        return new SnapshotResult(Optional.of(new Snapshot(followers, bio, er)), false);
    }

    private static Optional<Double> engagementRatio(String platform, JsonNode perf) {
        return switch (platform) {
            case INSTAGRAM -> {
                // Image posts are measured against followers. Reels are measured against views,
                // so for an account with no recent images the per-follower figures are summed
                // instead, which is the same definition.
                Optional<Double> images = positive(perf.path("imagesPerformanceRecent")
                        .path("engagement").path("avgEngagementRate"));
                if (images.isPresent()) {
                    yield images;
                }
                JsonNode reels = perf.path("reelsPerformanceRecent").path("engagement");
                double perFollower = reels.path("likesPerFollower").asDouble(0)
                        + reels.path("commentsPerFollower").asDouble(0);
                yield perFollower > 0 ? Optional.of(perFollower) : Optional.empty();
            }
            case YOUTUBE -> positive(perf.path("videosPerformanceRecent").path("engagement").path("avgEngagementRate"))
                    .or(() -> positive(perf.path("shortsPerformanceRecent").path("engagement").path("avgEngagementRate")));
            default -> positive(perf.path("videosPerformanceRecent").path("engagement").path("avgEngagementRate"));
        };
    }

    // =====================================================================
    // Audience demographics: 10 credits
    // =====================================================================

    /**
     * The same keys the connected-Instagram path writes: {@code ukAudiencePct},
     * {@code topLocation}, {@code topAgeBand}, {@code genderSplit}. Empty when CreatorDB has no
     * audience model for the creator, which happens for small accounts.
     */
    public Map<String, Object> audience(Target target) {
        return audienceRaw(target).data()
                .map(data -> summarise(target.platform(), breakdown(data)))
                .orElseGet(Map::of);
    }

    /** The {@code audience} call itself, for callers that store the answer. */
    public CreatorDbClient.Result audienceRaw(Target target) {
        return client.call(path(target, "audience"), identify(target));
    }

    /** Every share CreatorDB gave, largest first, as percentages. */
    public static AudienceBreakdown breakdown(JsonNode data) {
        List<Share> countries = new ArrayList<>();
        for (JsonNode row : data.path("audienceLocations")) {
            String code = row.path("country").asText("");
            if (!code.isEmpty()) {
                countries.add(new Share(country(code), percent(row.path("share").asDouble())));
            }
        }
        countries.sort((a, b) -> b.pct().compareTo(a.pct()));

        // Age bands keep CreatorDB's order (13-17, 18-24, ...), which is the order a reader expects.
        List<Share> ages = new ArrayList<>();
        for (JsonNode row : data.path("audienceAgeBreakdown")) {
            String range = row.path("ageRange").asText("");
            if (!range.isEmpty()) {
                ages.add(new Share(range, percent(row.path("share").asDouble())));
            }
        }

        JsonNode gender = data.path("audienceGender");
        boolean hasGender = gender.isObject() && (gender.has("femaleRatio") || gender.has("maleRatio"));
        return new AudienceBreakdown(countries, ages,
                hasGender ? oneDecimal(gender.path("femaleRatio").asDouble()) : null,
                hasGender ? oneDecimal(gender.path("maleRatio").asDouble()) : null,
                data.hasNonNull("audienceAvgAge") ? data.path("audienceAvgAge").asInt() : null);
    }

    /** The four summary figures the creator row stores, from a full breakdown. */
    public static Map<String, Object> summarise(String platform, AudienceBreakdown audience) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!audience.countries().isEmpty()) {
            // Requirement #26 asks for the UK share specifically, which is rarely the largest.
            audience.countries().stream()
                    .filter(share -> "GB".equals(share.key()))
                    .findFirst()
                    .ifPresent(uk -> out.put("ukAudiencePct", uk.pct().doubleValue()));
            Share top = audience.countries().getFirst();
            out.put("topLocation", top.key() + " (" + plain(top.pct()) + "%)");
        }
        audience.ageBands().stream()
                .reduce((a, b) -> b.pct().compareTo(a.pct()) > 0 ? b : a)
                .ifPresent(top -> out.put("topAgeBand", top.key() + " (" + plain(top.pct()) + "%)"));
        if (audience.femalePct() != null) {
            out.put("genderSplit", "Female " + audience.femalePct().toPlainString()
                    + "%, Male " + audience.malePct().toPlainString() + "%");
        }
        if (audience.averageAge() != null) {
            out.put("averageAge", audience.averageAge());
        }
        if (!out.isEmpty()) {
            out.put("platform", platform);
            out.put("source", "CREATORDB");
        }
        return out;
    }

    // =====================================================================
    // Recent posts for clipping: 3 credits per platform
    // =====================================================================

    public List<Map<String, Object>> recentPosts(Target target, int limit) {
        String key = target.platform() + ":" + target.handle().toLowerCase();
        CachedPosts cached = postCache.get(key);
        if (cached != null && cached.at().isAfter(Instant.now().minus(Duration.ofMinutes(postCacheMinutes)))) {
            return cached.posts();
        }

        // Only successes are cached: an empty answer is free, and a retry may find new posts.
        List<Map<String, Object>> posts = client.post(path(target, "content-detail"), identify(target))
                .map(data -> mapPosts(target, data, limit))
                .orElseGet(List::of);
        if (!posts.isEmpty()) {
            postCache.put(key, new CachedPosts(posts, Instant.now()));
        }
        return posts;
    }

    private List<Map<String, Object>> mapPosts(Target target, JsonNode data, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (target.platform()) {
            case INSTAGRAM -> {
                addPosts(out, target, data.path("recentReels"), "REEL", "https://www.instagram.com/reel/");
                addPosts(out, target, data.path("recentImages"), "POST", "https://www.instagram.com/p/");
            }
            case TIKTOK -> addPosts(out, target, data.path("recentVideos"), "TIKTOK",
                    "https://www.tiktok.com/@" + target.handle() + "/video/");
            case YOUTUBE -> {
                addPosts(out, target, data.path("recentVideos"), "YOUTUBE", "https://www.youtube.com/watch?v=");
                addPosts(out, target, data.path("recentShorts"), "YOUTUBE_SHORT", "https://www.youtube.com/shorts/");
            }
            default -> { }
        }
        out.sort((a, b) -> String.valueOf(b.get("postedAt")).compareTo(String.valueOf(a.get("postedAt"))));
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
    }

    private static void addPosts(List<Map<String, Object>> out, Target target, JsonNode items,
                                 String postType, String urlPrefix) {
        if (!items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            String id = item.path("contentId").asText(null);
            if (id == null || id.isBlank()) {
                continue;
            }
            // Documented as a URL, returned as a bare id in their examples; accept either.
            String url = id.startsWith("http") ? id : urlPrefix + id;
            String externalId = id.startsWith("http") ? id.replaceAll("/+$", "").replaceAll(".*/", "") : id;

            Map<String, Object> post = new LinkedHashMap<>();
            post.put("id", externalId);
            post.put("platform", target.platform());
            post.put("postType", postType);
            post.put("handle", target.handle());
            post.put("url", url);
            String caption = item.hasNonNull("title") ? item.path("title").asText() : item.path("description").asText(null);
            post.put("caption", truncate(caption));
            post.put("likes", item.path("likes").asLong(0));
            post.put("comments", item.path("comments").asLong(0));
            // Absent, not zero, when the platform does not report it — Instagram images have no views.
            if (item.hasNonNull("views")) {
                post.put("views", item.path("views").asLong());
            }
            if (item.hasNonNull("shares")) {
                post.put("shares", item.path("shares").asLong());
            }
            long published = item.path("publishTime").asLong(0);
            post.put("postedAt", (published > 0 ? Instant.ofEpochMilli(published) : Instant.now()).toString());
            out.add(post);
        }
    }

    // =====================================================================
    // Discovery: 1 credit per search, without enrichment
    // =====================================================================

    /**
     * The legacy provider signature: free text and a niche, both read as hashtags.
     */
    public List<Map<String, Object>> search(String query, String platform, String niche) {
        String keywords = String.join(" ", query == null ? "" : query, niche == null ? "" : niche);
        return discover(new DiscoveryQuery(platform, keywords, null, null, null, 0)).creators().stream()
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("handle", c.handle());
                    row.put("name", c.name());
                    row.put("platform", c.platform());
                    row.put("followers", c.followers());
                    if (c.avatarUrl() != null) {
                        row.put("avatarUrl", c.avatarUrl());
                    }
                    if (c.channelId() != null) {
                        row.put("channelId", c.channelId());
                    }
                    return row;
                })
                .toList();
    }

    /**
     * Creators CreatorDB knows, filtered and sorted by size.
     *
     * <p>Results carry handle, name, avatar and follower count only. Asking for more per result
     * is charged per result, so detail is fetched for the creators someone actually adds. Up to
     * ten filters cost one credit; this never sends more than five.
     */
    public DiscoveryPage discover(DiscoveryQuery query) {
        String platform = query.platform() == null || query.platform().isBlank()
                ? INSTAGRAM : query.platform().trim().toUpperCase();
        if (!PLATFORMS.contains(platform)) {
            return DiscoveryPage.empty();
        }
        String sizeField = YOUTUBE.equals(platform) ? "totalSubscribers" : "totalFollowers";

        List<Map<String, Object>> filters = new ArrayList<>();
        List<String> terms = keywordsOf(query.keywords());
        if (!terms.isEmpty()) {
            filters.add(Map.of("filterName", "hashtags", "op", "in", "value", terms));
        }
        String country = alpha3(query.country());
        if (country != null) {
            filters.add(Map.of("filterName", "country", "op", "=", "value", country));
        }
        if (query.minFollowers() != null && query.minFollowers() > 0) {
            filters.add(Map.of("filterName", sizeField, "op", ">", "value", query.minFollowers()));
        }
        if (query.maxFollowers() != null && query.maxFollowers() > 0) {
            filters.add(Map.of("filterName", sizeField, "op", "<", "value", query.maxFollowers()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("filters", filters);
        body.put("sortBy", sizeField);
        body.put("desc", true);
        body.put("pageSize", Math.clamp(searchPageSize, 1, 100));
        body.put("offset", Math.max(query.offset(), 0));

        return client.post(platform.toLowerCase() + "/search", body)
                .map(data -> {
                    List<DiscoveredCreator> creators = new ArrayList<>();
                    for (JsonNode c : data.path("creatorList")) {
                        // YouTube handles come back as "@name"; the others bare. Stored bare.
                        String handle = c.path("uniqueId").asText("").trim().replaceFirst("^@", "");
                        if (handle.isBlank()) {
                            continue;
                        }
                        long size = c.path(sizeField).asLong(c.path("totalFollowers").asLong(0));
                        creators.add(new DiscoveredCreator(platform, handle,
                                c.path("displayName").asText(handle), size,
                                c.hasNonNull("avatarUrl") ? c.path("avatarUrl").asText() : null,
                                c.hasNonNull("channelId") ? c.path("channelId").asText() : null));
                    }
                    return new DiscoveryPage(creators, data.path("totalResults").asLong(creators.size()),
                            data.path("hasNextPage").asBoolean(false),
                            data.hasNonNull("nextOffset") ? data.path("nextOffset").asInt() : null);
                })
                .orElseGet(DiscoveryPage::empty);
    }

    static List<String> keywordsOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(t -> t.replaceFirst("^#", "").trim().toLowerCase())
                .filter(t -> !t.isBlank())
                .distinct()
                .limit(10)
                .toList();
    }

    // =====================================================================
    // Submitting creators CreatorDB has not indexed: free
    // =====================================================================

    /**
     * Asks CreatorDB to index a creator it does not know. Free whatever the outcome; indexing
     * takes a while, so the next refresh rather than this one benefits.
     *
     * @return {@code accepted}, {@code done} (already indexed), {@code rejected}, or empty when the
     *         call itself failed
     */
    public Optional<String> submit(Target target) {
        Map<String, Object> body = YOUTUBE.equals(target.platform()) && isChannelId(target.handle())
                ? Map.of("channelIds", List.of(target.handle()))
                : Map.of("uniqueIds", List.of(target.handle()));
        Optional<String> status = client.post(path(target, "submitCreators"), body)
                .map(data -> data.path("results").path(0).path("status").asText(""))
                .filter(s -> !s.isBlank());
        status.ifPresent(s -> log.info("Submitted {} @{} to CreatorDB for indexing: {}",
                target.platform(), target.handle(), s));
        return status;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String path(Target target, String endpoint) {
        return target.platform().toLowerCase() + "/" + endpoint;
    }

    /** YouTube takes a channel id or a handle; the other two only a handle. */
    private static Map<String, Object> identify(Target target) {
        String handle = target.handle();
        if (YOUTUBE.equals(target.platform()) && isChannelId(handle)) {
            return Map.of("channelId", handle);
        }
        return Map.of("uniqueId", handle);
    }

    private static boolean isChannelId(String handle) {
        return handle.startsWith("UC") && handle.length() == 24;
    }

    private static Optional<Double> positive(JsonNode node) {
        return node.isNumber() && node.asDouble() > 0 ? Optional.of(node.asDouble()) : Optional.empty();
    }

    private static BigDecimal percent(double ratio) {
        return BigDecimal.valueOf(ratio * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal oneDecimal(double ratio) {
        return BigDecimal.valueOf(ratio * 100).setScale(1, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    static String country(String alpha3) {
        return ALPHA3_TO_ALPHA2.getOrDefault(alpha3.toUpperCase(), alpha3);
    }

    /** {@code GB} or {@code GBR} to {@code GBR}; null for anything unrecognised. */
    static String alpha3(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String cleaned = code.trim().toUpperCase();
        if (cleaned.length() == 3 && ALPHA3_TO_ALPHA2.containsKey(cleaned)) {
            return cleaned;
        }
        if (cleaned.length() == 2 && Arrays.asList(Locale.getISOCountries()).contains(cleaned)) {
            return Locale.of("", cleaned).getISO3Country();
        }
        return null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 997) + "...";
    }
}
