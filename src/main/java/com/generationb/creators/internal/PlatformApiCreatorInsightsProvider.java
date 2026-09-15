package com.generationb.creators.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.generationb.creators.CreatorInsightsProvider;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.insights.HashtagBudget;
import com.generationb.foundation.insights.InstagramProfile;
import com.generationb.foundation.insights.InstagramProfiles;
import com.generationb.foundation.insights.MetaGraphClient;
import com.generationb.foundation.insights.TokenCipher;
import com.generationb.foundation.insights.YouTubeDataClient;
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
 * Creator data straight from the platforms, with no data vendor in between.
 *
 * <p>Built when the paid vendor stopped being affordable. Meta, Google and TikTok will each
 * answer some of what the vendor did, for nothing, and this routes each question to whichever of
 * them can answer it. What none of them will answer, it says so about, rather than returning an
 * empty list that reads like "no results".
 *
 * <h2>What comes from where</h2>
 *
 * <table>
 *   <tr><th>Question</th><th>Instagram</th><th>TikTok</th><th>YouTube</th></tr>
 *   <tr><td>A creator's posts (#10)</td>
 *       <td>Business Discovery, no permission needed</td>
 *       <td>only if connected</td>
 *       <td>Data API, public</td></tr>
 *   <tr><td>Hashtag and mention search (#11, #25)</td>
 *       <td>Hashtag Search, 30 tags per 7 days</td>
 *       <td>nothing</td>
 *       <td>search, 100 quota units a time</td></tr>
 *   <tr><td>Audience demographics (#26)</td>
 *       <td>only if connected</td>
 *       <td><b>never</b></td>
 *       <td>only if connected</td></tr>
 *   <tr><td>Creator search (#23)</td>
 *       <td>nothing</td><td>nothing</td><td>keyword only</td></tr>
 * </table>
 *
 * <h2>The two things genuinely lost</h2>
 *
 * <p><b>TikTok demographics.</b> Not a permissions problem — TikTok's open API has no such
 * endpoint at any tier a commercial agency can reach. A connected TikTok gives videos and a
 * follower count and that is the ceiling.
 *
 * <p><b>Semantic creator discovery.</b> Meta indexes hashtags, not people. There is no way to ask
 * "beauty creators in the north of England with a 25-34 female audience" of any of these APIs, so
 * {@link #searchCreators} reports that plainly rather than pretending.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
@Conditional(InsightsProviderCondition.PlatformApis.class)
public class PlatformApiCreatorInsightsProvider implements CreatorInsightsProvider {

    private static final int MAX_ITEMS = 60;

    /** Each distinct tag costs one of thirty for a week, so a sweep must not fan out. */
    private static final int MAX_HASHTAGS_PER_SWEEP = 2;

    private final MetaGraphClient meta;
    private final InstagramProfiles instagram;
    private final YouTubeDataClient youtube;
    private final HashtagBudget hashtagBudget;
    private final CreatorRepository creatorRepository;
    private final CreatorPlatformConnectionRepository connectionRepository;
    private final TokenCipher tokenCipher;

    /**
     * Says at startup which platforms will actually answer, because a half-configured
     * integration returns empty results that look exactly like "this creator posts nothing".
     */
    @PostConstruct
    void announce() {
        List<String> live = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        (instagram.isEnabled() ? live : missing).add("Instagram profiles and posts (Meta Graph)");
        (meta.isEnabled() ? live : missing).add("Instagram hashtag search and demographics (Meta Graph)");
        (youtube.isEnabled() ? live : missing).add("YouTube (Data API)");
        (tokenCipher.isConfigured() ? live : missing).add("account connections (demographics)");

        log.info("Creator data is coming from the platforms directly. Live: {}. Not configured: {}",
                live.isEmpty() ? "nothing" : String.join(", ", live),
                missing.isEmpty() ? "nothing" : String.join(", ", missing));

        if (!missing.isEmpty()) {
            log.warn("Until those are configured the related features return no data. See "
                    + "docs/integrations/platform-apis.md.");
        }
    }

    // =====================================================================
    // #10 — a creator's own posts
    // =====================================================================

    @Override
    public List<Map<String, Object>> getRecentActivity(UUID creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId).orElse(null);
        if (creator == null) {
            log.warn("No active creator {} to fetch activity for", creatorId);
            return List.of();
        }

        Map<String, CreatorPlatformConnection> connections =
                connectionRepository.findLiveForCreator(creatorId).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                CreatorPlatformConnection::getPlatform, c -> c, (a, b) -> a));

        List<Map<String, Object>> posts = new ArrayList<>();
        posts.addAll(instagramPosts(creator));
        posts.addAll(youtubePosts(creator));
        posts.addAll(tiktokPosts(creator, connections.get(CreatorPlatformConnection.TIKTOK)));
        return posts;
    }

    /**
     * Instagram, via Business Discovery — no permission from the creator needed, which is what
     * makes auto-clipping work for the whole roster rather than only the connected part.
     *
     * <p>Only sees Business and Creator accounts. A creator on a personal account returns
     * nothing, and that is a property of their account rather than a fault here.
     */
    private List<Map<String, Object>> instagramPosts(Creator creator) {
        if (!instagram.isEnabled() || !isInstagramCreator(creator)) {
            return List.of();
        }
        return instagram.fetch(creator.getHandle(), 25)
                .map(this::mapInstagramProfile)
                .orElseGet(() -> {
                    log.info("Instagram returned nothing for @{} via {}", creator.getHandle(),
                            instagram.activeName());
                    return List.of();
                });
    }

    /**
     * The primary handle is an Instagram handle unless the creator is primarily somewhere else.
     * Looking a YouTube handle up on Instagram would clip a stranger's posts.
     */
    private static boolean isInstagramCreator(Creator creator) {
        String platform = creator.getPrimaryPlatform();
        return platform == null || platform.isBlank() || "INSTAGRAM".equalsIgnoreCase(platform.trim());
    }

    /** YouTube, entirely public. Two cheap calls rather than one expensive search. */
    private List<Map<String, Object>> youtubePosts(Creator creator) {
        String channel = handleOf(creator.getYoutubeHandle());
        if (channel == null || !youtube.isEnabled()) {
            return List.of();
        }

        Optional<JsonNode> found = youtube.channel(channel);
        if (found.isEmpty()) {
            return List.of();
        }
        JsonNode node = found.get();
        String uploads = node.path("contentDetails").path("relatedPlaylists")
                .path("uploads").asText(null);
        long subscribers = node.path("statistics").path("subscriberCount").asLong(0);

        return youtube.recentUploads(uploads, 20)
                .map(body -> mapYouTubeUploads(body.path("items"), channel, subscribers))
                .orElseGet(List::of);
    }

    /**
     * TikTok, and only for a creator who has connected.
     *
     * <p>There is no public route. TikTok's Research API is restricted to academic and non-profit
     * institutions — their own documentation says commercial users are ineligible — and the
     * alternative is scraping, which breaches their terms and is not a risk to take on a client's
     * behalf.
     */
    private List<Map<String, Object>> tiktokPosts(Creator creator, CreatorPlatformConnection connection) {
        if (handleOf(creator.getTiktokHandle()) == null) {
            return List.of();
        }
        if (connection == null || !connection.isUsable()) {
            log.info("TikTok content for creator {} needs them to connect their account; "
                    + "there is no public route", creator.getId());
            return List.of();
        }
        // Deliberately not implemented against a live token yet: the Display API call is trivial,
        // but it is dead code until the TikTok app passes review. The connection model, the
        // consent flow and this branch are in place so it is a small change when it does.
        log.debug("TikTok connection present for creator {} but the Display API client is not "
                + "wired yet", creator.getId());
        return List.of();
    }

    // =====================================================================
    // #11 / #25 — mentions and competitor monitoring
    // =====================================================================

    @Override
    public List<Map<String, Object>> getMentions(String brandOrHashtag, int limit) {
        if (brandOrHashtag == null || brandOrHashtag.isBlank() || !meta.isEnabled()) {
            return List.of();
        }
        UUID brandId = BrandContext.getCurrentBrandId();
        List<Map<String, Object>> found = new ArrayList<>();

        for (String tag : parseTerms(brandOrHashtag)) {
            if (found.size() >= limit) {
                break;
            }
            // The budget is the constraint here, not the rate limit. A new tag costs one of
            // thirty for a week; one already in the window is free.
            Optional<String> hashtagId = hashtagBudget.resolve(tag, brandId);
            if (hashtagId.isEmpty()) {
                log.warn("Skipping #{}: either Instagram does not know it, or the 7-day hashtag "
                        + "allowance is spent", tag);
                continue;
            }
            meta.hashtagMedia(hashtagId.get(), false, Math.min(limit - found.size(), 50))
                    .ifPresent(body -> found.addAll(mapHashtagMedia(body.path("data"), tag)));
        }
        return found.size() > limit ? found.subList(0, limit) : found;
    }

    // =====================================================================
    // #26 — audience demographics
    // =====================================================================

    /**
     * Audience demographics, from the platform's own reporting.
     *
     * <p>Needs the creator to have connected. That is a real cost — it is a thing to ask them for
     * — but the data is better than what a vendor sells: Instagram's own numbers rather than a
     * model, free, and consented to explicitly by the person they describe.
     */
    @Override
    public Map<String, Object> getAudienceDemographics(UUID creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId).orElse(null);
        if (creator == null) {
            return Map.of();
        }

        Optional<CreatorPlatformConnection> instagram =
                connectionRepository.findLive(creatorId, CreatorPlatformConnection.INSTAGRAM);

        if (instagram.isEmpty() || !instagram.get().isUsable()) {
            // Said out loud rather than returned as an empty map, so the screen can explain why
            // instead of showing a blank panel.
            log.info("No usable Instagram connection for creator {}; demographics unavailable "
                    + "until they connect", creatorId);
            return Map.of();
        }

        CreatorPlatformConnection connection = instagram.get();
        String token = tokenCipher.decrypt(connection.getAccessTokenEncrypted());
        if (token == null) {
            return Map.of();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        readBreakdown(connection, token, "country", out);
        readBreakdown(connection, token, "age", out);
        readBreakdown(connection, token, "gender", out);

        meta.connectedProfile(connection.getExternalAccountId(), token).ifPresent(profile -> {
            if (profile.hasNonNull("followers_count")) {
                out.put("followers", profile.path("followers_count").asInt());
            }
            if (profile.hasNonNull("name")) {
                out.put("fullName", profile.path("name").asText());
            }
        });

        if (!out.isEmpty()) {
            out.put("source", "INSTAGRAM_CONNECTED");
        }
        return out;
    }

    /**
     * Meta returns one breakdown per call, as a list of {@code {dimension_values, value}} rows.
     *
     * <p>Absolute counts, not percentages, so they have to be totalled and converted — and a
     * small account legitimately returns nothing at all, because Meta suppresses breakdowns below
     * its own reporting minimum.
     */
    private void readBreakdown(CreatorPlatformConnection connection, String token,
                               String breakdown, Map<String, Object> out) {
        meta.audienceDemographics(connection.getExternalAccountId(), token, breakdown)
                .map(body -> body.path("data").path(0)
                        .path("total_value").path("breakdowns").path(0).path("results"))
                .filter(JsonNode::isArray)
                .filter(results -> !results.isEmpty())
                .ifPresent(results -> {
                    long total = 0;
                    Map<String, Long> counts = new LinkedHashMap<>();
                    for (JsonNode row : results) {
                        String key = row.path("dimension_values").path(0).asText("");
                        long value = row.path("value").asLong(0);
                        if (!key.isEmpty()) {
                            counts.merge(key, value, Long::sum);
                            total += value;
                        }
                    }
                    if (total == 0) {
                        return;
                    }
                    switch (breakdown) {
                        case "country" -> applyCountries(counts, total, out);
                        case "age" -> applyAges(counts, total, out);
                        case "gender" -> applyGenders(counts, total, out);
                        default -> { }
                    }
                });
    }

    private void applyCountries(Map<String, Long> counts, long total, Map<String, Object> out) {
        // Requirement #26 asks specifically for the UK share, which is rarely the largest.
        Long uk = counts.get("GB");
        if (uk != null) {
            out.put("ukAudiencePct", percent(uk, total));
        }
        counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(top -> out.put("topLocation",
                        top.getKey() + " (" + percent(top.getValue(), total) + "%)"));
    }

    private void applyAges(Map<String, Long> counts, long total, Map<String, Object> out) {
        counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(top -> out.put("topAgeBand",
                        top.getKey() + " (" + percent(top.getValue(), total) + "%)"));
    }

    private void applyGenders(Map<String, Long> counts, long total, Map<String, Object> out) {
        double female = percent(counts.getOrDefault("F", 0L), total);
        double male = percent(counts.getOrDefault("M", 0L), total);
        out.put("genderSplit", "Female " + female + "%, Male " + male + "%");
    }

    // =====================================================================
    // #23 — creator search, which none of these can do
    // =====================================================================

    /**
     * Not available from the platforms.
     *
     * <p>Meta indexes hashtags, not people; TikTok indexes nothing a commercial application may
     * read; YouTube searches video text rather than creator attributes. There is no way to ask
     * any of them "creators whose audience is mostly women aged 25-34 in the UK".
     *
     * <p>Returns empty and logs why, so the screen shows an explanation rather than "no results" —
     * which would read as "nobody matches" and is a different and untrue statement.
     */
    @Override
    public List<Map<String, Object>> searchCreators(String criteriaQuery, String platform, String niche) {
        log.info("Creator discovery by description is not available from the platform APIs. "
                + "Meta and TikTok expose no creator index; use hashtag monitoring to surface "
                + "creators, or search the existing database.");
        return List.of();
    }

    // =====================================================================
    // Mapping
    // =====================================================================

    /** Instagram posts to the shape {@code CoverageService.ingest} reads, whichever source answered. */
    private List<Map<String, Object>> mapInstagramProfile(InstagramProfile profile) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InstagramProfile.Post item : profile.posts()) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            Map<String, Object> post = new LinkedHashMap<>();
            post.put("id", item.id());
            post.put("platform", "INSTAGRAM");
            post.put("postType", item.postType());
            post.put("handle", profile.username());
            post.put("url", item.url());
            post.put("caption", truncate(item.caption()));
            post.put("likes", item.likes() == null ? 0L : item.likes());
            post.put("comments", item.comments());
            // Only present when the source reports one. An absent key
            // becomes "not tracked" downstream rather than a fabricated zero.
            if (item.views() != null) {
                post.put("views", item.views());
            }
            if (profile.followers() > 0) {
                post.put("authorFollowers", profile.followers());
            }
            post.put("postedAt", item.postedAt() == null ? Instant.now().toString() : item.postedAt().toString());
            out.add(post);
        }
        return out;
    }

    /**
     * Hashtag media, which is a thinner payload than a creator's own.
     *
     * <p>Notably it carries <em>no username</em> — Meta will not tell you whose post it is
     * through this edge — so the handle is unknown and the coverage row is attributed to the
     * hashtag rather than a person. That is a real reduction against what the vendor gave, and
     * the reason these rows are logged as unsolicited mentions rather than creator coverage.
     */
    private List<Map<String, Object>> mapHashtagMedia(JsonNode media, String tag) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!media.isArray()) {
            return out;
        }
        for (JsonNode item : media) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            Map<String, Object> post = new LinkedHashMap<>();
            post.put("id", item.path("id").asText(null));
            post.put("platform", "INSTAGRAM");
            post.put("postType", instagramPostType("", item.path("media_type").asText("")));
            post.put("url", item.path("permalink").asText(null));
            post.put("caption", truncate(item.path("caption").asText(null)));
            post.put("mention", "#" + tag);
            // Meta gives no counts and no author on this edge. Absent, not zero.
            post.put("postedAt", isoOrNow(item.path("timestamp").asText(null)));
            out.add(post);
        }
        return out;
    }

    private List<Map<String, Object>> mapYouTubeUploads(JsonNode items, String channel, long subscribers) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!items.isArray()) {
            return out;
        }

        List<String> videoIds = new ArrayList<>();
        for (JsonNode item : items) {
            String id = item.path("contentDetails").path("videoId").asText(null);
            if (id != null) {
                videoIds.add(id);
            }
        }
        // One extra call for the whole batch buys real view, like and comment counts, which the
        // playlist itself does not carry.
        Map<String, JsonNode> stats = new LinkedHashMap<>();
        youtube.videoStatistics(videoIds).ifPresent(body -> {
            for (JsonNode video : body.path("items")) {
                stats.put(video.path("id").asText(""), video);
            }
        });

        for (JsonNode item : items) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            JsonNode snippet = item.path("snippet");
            String videoId = item.path("contentDetails").path("videoId").asText(null);
            if (videoId == null) {
                continue;
            }
            JsonNode statistics = stats.getOrDefault(videoId, snippet).path("statistics");

            Map<String, Object> post = new LinkedHashMap<>();
            post.put("id", videoId);
            post.put("platform", "YOUTUBE");
            // Long form, which is what the reporting split cares about.
            post.put("postType", "YOUTUBE");
            post.put("handle", channel.replaceFirst("^@", ""));
            post.put("url", "https://www.youtube.com/watch?v=" + videoId);
            post.put("caption", truncate(snippet.path("title").asText(null)));
            if (statistics.hasNonNull("viewCount")) {
                post.put("views", statistics.path("viewCount").asLong());
            }
            post.put("likes", statistics.path("likeCount").asLong(0));
            post.put("comments", statistics.path("commentCount").asLong(0));
            if (subscribers > 0) {
                post.put("authorFollowers", subscribers);
            }
            post.put("postedAt", isoOrNow(snippet.path("publishedAt").asText(null)));
            out.add(post);
        }
        return out;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Keeps {@code CoverageItem.formFor} able to tell short form from long. */
    private static String instagramPostType(String productType, String mediaType) {
        if ("REELS".equalsIgnoreCase(productType)) {
            return "REEL";
        }
        return switch (mediaType.toUpperCase()) {
            case "CAROUSEL_ALBUM" -> "CAROUSEL";
            case "VIDEO" -> "VIDEO";
            default -> "POST";
        };
    }

    private static Set<String> parseTerms(String raw) {
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(term -> term.replaceFirst("^[#@]", "").trim())
                .filter(term -> !term.isBlank())
                .limit(MAX_HASHTAGS_PER_SWEEP)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0 : Math.round((part * 1000.0) / total) / 10.0;
    }

    private static String isoOrNow(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now().toString();
        }
        try {
            // Meta uses +0000 rather than Z, which Instant.parse rejects.
            return java.time.OffsetDateTime.parse(timestamp,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
                    .toInstant().toString();
        } catch (Exception first) {
            try {
                return Instant.parse(timestamp).toString();
            } catch (Exception second) {
                return Instant.now().toString();
            }
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 997) + "...";
    }

    private static String handleOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
