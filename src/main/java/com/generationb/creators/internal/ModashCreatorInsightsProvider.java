package com.generationb.creators.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.generationb.creators.CreatorInsightsProvider;
import com.generationb.foundation.insights.ModashClient;
import com.generationb.foundation.insights.ModashClient.Meter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The real creator-data feed: Modash. Replaces {@link MockCreatorInsightsProvider} whenever a key
 * is configured, which is what unblocks requirements #10, #11, #25 and #26.
 *
 * <h2>Which endpoint answers which requirement</h2>
 * <ul>
 *   <li><b>#10 auto-clip</b> — {@code /raw/ig/user-feed} and {@code /raw/tiktok/user-feed}. The
 *       raw feeds are metered as raw requests rather than credits, so clipping a whole campaign
 *       does not eat the report allowance.
 *   <li><b>#11 hashtag and mention discovery</b> — {@code /raw/ig/hashtag-feed} for the brand's
 *       monitored hashtags and {@code /raw/ig/user-tags-feed} for posts that tag the brand.
 *   <li><b>#25 competitor mentions</b> — the same hashtag feed, pointed at a competitor's tag.
 *   <li><b>#26 audience demographics</b> — {@code /instagram/profile/{handle}/report}, the only
 *       call here that costs a whole credit, which is why enrichment is deliberate rather than
 *       something a list screen triggers.
 *   <li><b>#23 natural-language search</b> — {@code /ai/instagram/text-search}, which reads a
 *       sentence rather than matching words against five columns.
 * </ul>
 *
 * <h2>What it will not do</h2>
 * <p>Instagram publishes no view count on the raw feed — not for carousels, not for reels, on
 * brand accounts or creator accounts. This maps that to an <em>absent</em> view count rather than
 * zero. A report reading "0 views" tells a client the campaign was not seen; "not tracked" tells
 * them the truth. TikTok does supply {@code stats.playCount}, so TikTok rows carry real views.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
@Conditional(InsightsProviderCondition.Modash.class)
public class ModashCreatorInsightsProvider implements CreatorInsightsProvider {

    /** Modash's own page size on the raw feeds. Kept as a sanity cap on what we ingest at once. */
    private static final int MAX_ITEMS = 60;

    /** One credit a report. A hashtag sweep that fans out to twenty tags is a bill, not a search. */
    private static final int MAX_HASHTAGS_PER_SWEEP = 3;

    private static final double COST_REPORT = 1.0;
    private static final double COST_RAW = 1.0;
    private static final double COST_AI_SEARCH_PER_PROFILE = 0.025;

    private final ModashClient modash;
    private final CreatorRepository creatorRepository;

    /**
     * Says out loud, once, which of the two switches is set. Choosing the vendor and forgetting
     * the key leaves auto-clipping and demographics silently inert, which is the kind of failure
     * that gets noticed a week later in a client meeting.
     */
    @PostConstruct
    void announce() {
        if (!modash.isEnabled()) {
            log.error("insights.provider=modash but no API key is set. Auto-clipping, mention "
                    + "discovery and audience demographics will all return nothing until "
                    + "MODASH_API_KEY is configured.");
            return;
        }
        modash.budget().ifPresentOrElse(
                budget -> log.info("Modash connected: {} credit(s) and {} raw request(s) remaining",
                        budget.credits(), budget.rawRequests()),
                () -> log.warn("Modash key is set but the account balance could not be read."));
    }

    // =====================================================================
    // #10 — a creator's own posts
    // =====================================================================

    @Override
    public List<Map<String, Object>> getRecentActivity(UUID creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId).orElse(null);
        if (creator == null) {
            log.warn("No active creator {} to clip activity for", creatorId);
            return List.of();
        }

        List<Map<String, Object>> posts = new ArrayList<>();
        String instagram = handleOf(creator.getHandle());
        String tiktok = handleOf(creator.getTiktokHandle());

        // A creator whose primary platform is TikTok often still has the Instagram handle in the
        // main column, so both are tried rather than switching on primaryPlatform.
        if (instagram != null) {
            posts.addAll(instagramFeed(instagram, creator.getFollowersCount()));
        }
        if (tiktok != null) {
            posts.addAll(tiktokFeed(tiktok));
        }
        if (posts.isEmpty()) {
            log.info("Modash returned no recent activity for creator {}", creatorId);
        }
        return posts;
    }

    /**
     * @param followers the creator's known follower count, passed through so coverage can work
     *                  out an engagement rate. Instagram gives no view count to divide by, and
     *                  engagements over followers is the convention on that platform anyway.
     */
    private List<Map<String, Object>> instagramFeed(String handle, Integer followers) {
        return modash.get("/raw/ig/user-feed", Map.of("url", handle), Meter.RAW, COST_RAW)
                .map(body -> mapInstagramItems(body.path("items"), handle).stream()
                        .peek(post -> {
                            if (followers != null && followers > 0) {
                                post.put("authorFollowers", followers.longValue());
                            }
                        })
                        .toList())
                .orElseGet(List::of);
    }

    private List<Map<String, Object>> tiktokFeed(String handle) {
        return modash.get("/raw/tiktok/user-feed", Map.of("url", handle), Meter.RAW, COST_RAW)
                .map(body -> mapTiktokItems(body.path("user_feed").path("items")))
                .orElseGet(List::of);
    }

    // =====================================================================
    // #11 / #25 — mentions of a brand, a hashtag or a competitor
    // =====================================================================

    @Override
    public List<Map<String, Object>> getMentions(String brandOrHashtag, int limit) {
        if (brandOrHashtag == null || brandOrHashtag.isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> found = new ArrayList<>();
        Set<String> terms = parseTerms(brandOrHashtag);

        for (String term : terms) {
            if (found.size() >= limit) {
                break;
            }
            found.addAll(hashtagFeed(term, limit - found.size()));
        }

        // Posts that tag the brand's own account are mentions the hashtag sweep will not see,
        // because tagging is not hashtagging.
        if (found.size() < limit && !terms.isEmpty()) {
            found.addAll(taggedFeed(terms.iterator().next(), limit - found.size()));
        }

        return found.size() > limit ? found.subList(0, limit) : found;
    }

    private List<Map<String, Object>> hashtagFeed(String tag, int limit) {
        return modash.get("/raw/ig/hashtag-feed", Map.of("hashtag", tag, "type", "recent"),
                        Meter.RAW, COST_RAW)
                .map(body -> mapInstagramItems(body.path("items"), null).stream()
                        .peek(post -> post.put("mention", "#" + tag))
                        .limit(limit)
                        .toList())
                .orElseGet(List::of);
    }

    private List<Map<String, Object>> taggedFeed(String handle, int limit) {
        return modash.get("/raw/ig/user-tags-feed", Map.of("url", handle), Meter.RAW, COST_RAW)
                .map(body -> mapInstagramItems(body.path("items"), null).stream()
                        .peek(post -> post.put("mention", "@" + handle))
                        .limit(limit)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * A brand's monitored-hashtags setting is free text — "#katieloxton, #KLgifting" or just the
     * brand name. Splits it into usable tags and caps the fan-out, because each one is a request.
     */
    private static Set<String> parseTerms(String raw) {
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(term -> term.replaceFirst("^[#@]", "").trim())
                .filter(term -> !term.isBlank())
                .limit(MAX_HASHTAGS_PER_SWEEP)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    // =====================================================================
    // #26 — audience demographics
    // =====================================================================

    @Override
    public Map<String, Object> getAudienceDemographics(UUID creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId).orElse(null);
        String handle = creator == null ? null : handleOf(creator.getHandle());
        if (handle == null) {
            return Map.of();
        }
        return fetchReport(handle, platformPathOf(creator)).orElseGet(Map::of);
    }

    /**
     * The audience report, flattened.
     *
     * <p>Returns both the display strings the creator screen shows and the raw numbers the KPI
     * matcher compares, so {@code CreatorEnrichmentService} does not have to re-walk the payload.
     * Weights come back as fractions (0.68), and everything here is a percentage (68.0).
     */
    Optional<Map<String, Object>> fetchReport(String handle, String platformPath) {
        return modash.get("/" + platformPath + "/profile/" + handle + "/report",
                        Map.of(), Meter.DISCOVERY, COST_REPORT)
                .map(body -> body.path("profile"))
                .filter(profile -> !profile.isMissingNode() && !profile.isEmpty())
                .map(ModashCreatorInsightsProvider::mapReport);
    }

    private static Map<String, Object> mapReport(JsonNode profile) {
        Map<String, Object> out = new LinkedHashMap<>();
        JsonNode audience = profile.path("audience");
        JsonNode header = profile.path("profile");

        JsonNode uk = weightedEntry(audience.path("geoCountries"), "code", "GB");
        JsonNode topCountry = firstOf(audience.path("geoCountries"));
        JsonNode topAge = heaviest(audience.path("ages"));
        JsonNode female = weightedEntry(audience.path("genders"), "code", "FEMALE");
        JsonNode male = weightedEntry(audience.path("genders"), "code", "MALE");

        // --- display strings, unchanged in shape from the mock the screens were built against
        if (topCountry != null) {
            out.put("topLocation", topCountry.path("name").asText() + " ("
                    + percent(topCountry.path("weight").asDouble()) + "%)");
        }
        if (topAge != null) {
            out.put("topAgeBand", topAge.path("code").asText() + " ("
                    + percent(topAge.path("weight").asDouble()) + "%)");
        }
        if (female != null || male != null) {
            out.put("genderSplit", "Female " + percent(weightOf(female))
                    + "%, Male " + percent(weightOf(male)) + "%");
        }
        if (audience.hasNonNull("credibility")) {
            out.put("authenticityScore", percent(audience.path("credibility").asDouble()) + "%");
        }

        // --- the numbers the KPI matcher and the creator record actually store
        if (uk != null) {
            out.put("ukAudiencePct", percent(uk.path("weight").asDouble()));
        }
        if (audience.hasNonNull("credibility")) {
            out.put("credibilityPct", percent(audience.path("credibility").asDouble()));
        }
        if (header.hasNonNull("followers")) {
            out.put("followers", header.path("followers").asInt());
        }
        // Modash reports engagement rate as a fraction; every screen here shows a percentage.
        // Two decimals, not one: a 1M-follower account sits around 0.11%, and rounding that to
        // 0.1% loses the digit a KPI target is actually comparing against.
        if (header.hasNonNull("engagementRate")) {
            out.put("engagementRatePct", percent2(header.path("engagementRate").asDouble()));
        }
        if (header.hasNonNull("fullname")) {
            out.put("fullName", header.path("fullname").asText());
        }
        JsonNode topInterest = firstOf(profile.path("interests"));
        if (topInterest != null) {
            out.put("niche", topInterest.path("name").asText());
        }
        if (profile.hasNonNull("country")) {
            out.put("creatorCountry", profile.path("country").asText());
        }
        if (profile.hasNonNull("isVerified")) {
            out.put("verified", profile.path("isVerified").asBoolean());
        }
        contactValue(profile.path("contacts"), "email").ifPresent(email -> out.put("email", email));
        return out;
    }

    // =====================================================================
    // #23 — natural-language creator search
    // =====================================================================

    @Override
    public List<Map<String, Object>> searchCreators(String criteriaQuery, String platform, String niche) {
        String query = criteriaQuery == null ? "" : criteriaQuery.trim();
        if (niche != null && !niche.isBlank() && !query.toLowerCase().contains(niche.toLowerCase())) {
            query = (query + " " + niche).trim();
        }
        if (query.isBlank()) {
            return List.of();
        }

        String path = "instagram".equalsIgnoreCase(platformKey(platform)) ? "instagram" : platformKey(platform);
        int pageSize = 12;

        Map<String, Object> body = Map.of("query", query, "page", 0, "pageSize", pageSize);
        return modash.post("/ai/" + path + "/text-search", body,
                        Meter.DISCOVERY, COST_AI_SEARCH_PER_PROFILE * pageSize)
                .map(response -> mapAiSearchProfiles(response.path("profiles"), path))
                .orElseGet(List::of);
    }

    /**
     * AI search results.
     *
     * <p>Its field names are its own — {@code followersCount}, {@code fullName},
     * {@code profilePicture} — and, unlike the report, its {@code engagementRate} is <em>already
     * a percentage</em>. Verified against the live endpoint: an account with 6,310 followers
     * averaging ~100 likes comes back as 1.47, not 0.0147. Running it through the fraction
     * conversion turned a 1.5% rate into 147%.
     */
    private static List<Map<String, Object>> mapAiSearchProfiles(JsonNode profiles, String platform) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode p : profiles) {
            String username = p.path("username").asText(null);
            if (username == null || username.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("handle", username);
            row.put("name", firstNonBlank(p.path("fullName").asText(null), username));
            row.put("platform", platform.toUpperCase());
            row.put("followers", p.path("followersCount").asInt(0));
            if (p.path("engagementRate").isNumber()) {
                row.put("er", round1(p.path("engagementRate").asDouble()));
            }
            // A median view count is worth showing: it is the one audience-reach figure the
            // Instagram feeds never give us.
            if (p.path("viewsCountMedian").isNumber()) {
                row.put("medianViews", p.path("viewsCountMedian").asLong());
            }
            if (p.hasNonNull("userId")) {
                row.put("externalId", p.path("userId").asText());
            }
            if (p.hasNonNull("profilePicture")) {
                row.put("picture", p.path("profilePicture").asText());
            }
            out.add(row);
        }
        return out;
    }

    /**
     * The free {@code /{platform}/users} lookup, which uses yet another set of names:
     * {@code fullname}, {@code followers}, {@code picture}. It carries no engagement rate.
     */
    private static List<Map<String, Object>> mapUserLookup(JsonNode users, String platform) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode p : users) {
            String username = p.path("username").asText(null);
            if (username == null || username.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("handle", username);
            row.put("name", firstNonBlank(p.path("fullname").asText(null), username));
            row.put("platform", platform.toUpperCase());
            row.put("followers", p.path("followers").asInt(0));
            if (p.hasNonNull("userId")) {
                row.put("externalId", p.path("userId").asText());
            }
            if (p.hasNonNull("picture")) {
                row.put("picture", p.path("picture").asText());
            }
            if (p.path("isVerified").asBoolean(false)) {
                row.put("verified", true);
            }
            out.add(row);
        }
        return out;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Already a percentage — just tidy the decimals. */
    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * Handle lookup. Free, so it is safe to call while someone types, and it is how a handle a
     * user pasted becomes something the raw feeds will accept.
     */
    public List<Map<String, Object>> lookupHandles(String query, String platform, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return modash.get("/" + platformKey(platform) + "/users",
                        Map.of("query", query.replaceFirst("^@", ""),
                                "limit", String.valueOf(Math.clamp(limit, 1, 20))),
                        Meter.FREE, 0)
                .map(body -> mapUserLookup(body.path("users"), platformKey(platform)))
                .orElseGet(List::of);
    }

    /** What the account has left, so a screen that spends credits can show the balance. */
    public java.util.Optional<ModashClient.Budget> budget() {
        return modash.budget();
    }

    // =====================================================================
    // Mapping the post feeds
    // =====================================================================

    /**
     * Instagram raw feed to the shape {@code CoverageService.ingest} reads.
     *
     * <p>No view count is set. Instagram does not publish one here, and {@code ingest} treats an
     * absent key as "not tracked" rather than zero.
     */
    private static List<Map<String, Object>> mapInstagramItems(JsonNode items, String fallbackHandle) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            String code = item.path("code").asText(null);
            String productType = item.path("product_type").asText("feed");
            String handle = item.path("user").path("username").asText(fallbackHandle);

            Map<String, Object> post = new LinkedHashMap<>();
            post.put("id", item.path("pk").asText(code));
            post.put("platform", "INSTAGRAM");
            post.put("postType", instagramPostType(productType, item.path("media_type").asInt(1)));
            post.put("handle", handle);
            if (code != null) {
                post.put("url", "https://www.instagram.com/"
                        + ("clips".equals(productType) ? "reel/" : "p/") + code + "/");
            }
            post.put("caption", truncate(item.path("caption").path("text").asText(null)));
            post.put("likes", item.path("like_count").asLong(0));
            post.put("comments", item.path("comment_count").asLong(0));
            // Only present on the rare account that exposes it. Absent means absent.
            longIfPresent(item, "view_count").or(() -> longIfPresent(item, "play_count"))
                    .ifPresent(views -> post.put("views", views));
            post.put("postedAt", epochToInstant(item.path("taken_at").asLong(0)));
            if (item.path("is_paid_partnership").asBoolean(false)) {
                post.put("paidPartnership", true);
            }
            out.add(post);
        }
        return out;
    }

    /** TikTok does publish play counts, so these rows carry real views. */
    private static List<Map<String, Object>> mapTiktokItems(JsonNode items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            String id = item.path("id").asText(null);
            String author = item.path("author").path("uniqueId").asText(null);
            JsonNode stats = item.path("stats");

            Map<String, Object> post = new LinkedHashMap<>();
            post.put("id", id);
            post.put("platform", "TIKTOK");
            post.put("postType", "TIKTOK");
            post.put("handle", author);
            if (id != null && author != null) {
                post.put("url", "https://www.tiktok.com/@" + author + "/video/" + id);
            }
            post.put("caption", truncate(item.path("desc").asText(null)));
            post.put("likes", stats.path("diggCount").asLong(0));
            post.put("comments", stats.path("commentCount").asLong(0));
            post.put("shares", stats.path("shareCount").asLong(0));
            post.put("saves", stats.path("collectCount").asLong(0));
            longIfPresent(stats, "playCount").ifPresent(views -> post.put("views", views));
            longIfPresent(item.path("authorStats"), "followerCount")
                    .ifPresent(followers -> post.put("authorFollowers", followers));
            post.put("postedAt", epochToInstant(item.path("createTime").asLong(0)));
            out.add(post);
        }
        return out;
    }

    /** Keeps {@code CoverageItem.formFor} able to tell short form from long. */
    private static String instagramPostType(String productType, int mediaType) {
        return switch (productType) {
            case "clips" -> "REEL";
            case "igtv" -> "IGTV";
            case "carousel_container" -> "CAROUSEL";
            case "story" -> "STORY";
            default -> mediaType == 2 ? "VIDEO" : "POST";
        };
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String platformPathOf(Creator creator) {
        return platformKey(creator.getPrimaryPlatform());
    }

    private static String platformKey(String platform) {
        if (platform == null) {
            return "instagram";
        }
        return switch (platform.toUpperCase()) {
            case "TIKTOK" -> "tiktok";
            case "YOUTUBE" -> "youtube";
            default -> "instagram";
        };
    }

    private static String handleOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().replaceFirst("^@", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    /** The caption column is 1000 chars; a TikTok description can be longer. */
    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 997) + "...";
    }

    private static Optional<Long> longIfPresent(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? Optional.of(value.asLong()) : Optional.empty();
    }

    private static String epochToInstant(long epochSeconds) {
        return (epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now()).toString();
    }

    private static JsonNode firstOf(JsonNode array) {
        return array.isArray() && !array.isEmpty() ? array.get(0) : null;
    }

    private static JsonNode weightedEntry(JsonNode array, String field, String code) {
        if (!array.isArray()) {
            return null;
        }
        for (JsonNode entry : array) {
            if (code.equalsIgnoreCase(entry.path(field).asText())) {
                return entry;
            }
        }
        return null;
    }

    private static JsonNode heaviest(JsonNode array) {
        JsonNode best = null;
        if (array.isArray()) {
            for (JsonNode entry : array) {
                if (best == null || entry.path("weight").asDouble() > best.path("weight").asDouble()) {
                    best = entry;
                }
            }
        }
        return best;
    }

    private static double weightOf(JsonNode entry) {
        return entry == null ? 0 : entry.path("weight").asDouble();
    }

    /** One decimal place: 0.816242 becomes 81.6. Right for an audience weight. */
    private static double percent(double fraction) {
        return Math.round(fraction * 1000.0) / 10.0;
    }

    /** Two decimal places: 0.0421 becomes 4.21. Used for rates, where the second digit counts. */
    private static double percent2(double fraction) {
        return Math.round(fraction * 10000.0) / 100.0;
    }

    private static Optional<String> contactValue(JsonNode contacts, String type) {
        if (!contacts.isArray()) {
            return Optional.empty();
        }
        for (JsonNode contact : contacts) {
            if (type.equalsIgnoreCase(contact.path("type").asText())) {
                String value = contact.path("value").asText(null);
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }
}
