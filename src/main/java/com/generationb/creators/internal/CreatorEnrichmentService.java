package com.generationb.creators.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.generationb.creators.CreatorInsightsProvider;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.insights.InstagramProfile;
import com.generationb.foundation.insights.InstagramProfiles;
import com.generationb.foundation.insights.TokenCipher;
import com.generationb.foundation.insights.YouTubeDataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Requirement #26: keeps the creators already in the database up to date, for free and without
 * asking them for anything.
 *
 * <h2>What can be read without the creator's permission</h2>
 *
 * <ul>
 *   <li><b>Instagram</b> — Business Discovery returns follower count, bio and recent posts for any
 *       public <em>Business or Creator</em> account, looked up by handle and made as the agency's
 *       own Instagram Business account. Personal accounts return nothing; that is Meta's rule.
 *   <li><b>YouTube</b> — the Data API returns subscriber count and channel description for any
 *       public channel with a plain API key.
 *   <li><b>TikTok</b> — nothing. There is no official public route for a commercial app.
 * </ul>
 *
 * <p>Audience demographics (UK %, age, gender) are never public. They are only filled for a
 * creator who has connected their Instagram account, through the platform provider.
 *
 * <h2>Rules</h2>
 *
 * <p>The engagement rate is calculated here, from the posts the platforms return — never bought
 * from a vendor: (likes + comments) / followers on Instagram, (likes + comments) / views on
 * YouTube, averaged over the last {@value #RECENT_POSTS} posts. TikTok creators keep whatever was
 * entered by hand.
 *
 * <ul>
 *   <li>A creator refreshed inside {@code insights.enrichment.ttl-hours} is skipped unless forced.
 *   <li>A field no source answered for is left alone rather than blanked. A null from a platform
 *       having a bad day must not overwrite a figure someone researched by hand.
 *   <li>Bio is only filled when blank: a human's copy is usually better than the profile's.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorEnrichmentService {

    public record EnrichmentResult(
            UUID creatorId,
            String handle,
            boolean refreshed,
            String reason,
            Instant refreshedAt) {

        static EnrichmentResult skipped(Creator creator, String reason) {
            return new EnrichmentResult(creator.getId(), creator.getHandle(), false, reason,
                    creator.getInsightsRefreshedAt());
        }
    }

    /** Which free sources are configured, for the screen that offers the refresh. */
    public record SourceStatus(boolean live, boolean instagram, boolean youtube, boolean connections,
                               /** META when Instagram is live; null otherwise. */
                               String instagramSource) {
    }

    // Values written to creators.insights_source.
    static final String SOURCE_INSTAGRAM_PUBLIC = "INSTAGRAM_PUBLIC";
    static final String SOURCE_YOUTUBE_PUBLIC = "YOUTUBE_PUBLIC";
    static final String SOURCE_INSTAGRAM_CONNECTED = "INSTAGRAM_CONNECTED";

    private static final int MAX_BIO_LENGTH = 2000;

    /** How many recent posts the engagement rate is averaged over. */
    static final int RECENT_POSTS = 12;

    private final CreatorRepository creatorRepository;
    private final CreatorFollowerSnapshotRepository snapshotRepository;
    private final InstagramProfiles instagram;
    private final YouTubeDataClient youtube;
    private final TokenCipher tokenCipher;

    /** The platform provider when configured, otherwise the mock — whose demographics are fake. */
    private final CreatorInsightsProvider insightsProvider;

    @Value("${insights.enrichment.ttl-hours:20}")
    private int ttlHours;

    @Value("${insights.enrichment.max-batch:25}")
    private int maxBatch;

    public SourceStatus sourceStatus() {
        boolean instagramLive = instagram.isEnabled();
        boolean youtubeLive = youtube.isEnabled();
        return new SourceStatus(instagramLive || youtubeLive, instagramLive, youtubeLive,
                tokenCipher.isConfigured(), instagram.activeName());
    }

    // =====================================================================
    // One creator
    // =====================================================================

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public EnrichmentResult enrich(UUID creatorId, boolean force) {
        requireSources();
        Creator creator = creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator"));
        return enrichOne(creator, force);
    }

    private EnrichmentResult enrichOne(Creator creator, boolean force) {
        if (creator.isAnonymised()) {
            return EnrichmentResult.skipped(creator,
                    "This creator has been anonymised; their profile is not looked up again.");
        }
        if (!force && isFresh(creator)) {
            return EnrichmentResult.skipped(creator,
                    "Refreshed " + creator.getInsightsRefreshedAt() + "; still within "
                            + ttlHours + " hours.");
        }

        String platform = platformOf(creator);
        List<String> unanswered = new ArrayList<>();
        String source = null;

        // Instagram: the primary handle is an Instagram handle unless the creator is primarily on
        // another platform, in which case it is that platform's handle and must not be looked up.
        if ("INSTAGRAM".equals(platform) && instagram.isEnabled()) {
            String handle = cleanHandle(creator.getHandle());
            if (force) {
                instagram.evict(handle);
            }
            // One lookup returns the profile and its recent posts, which is what the engagement
            // rate is calculated from.
            Optional<InstagramProfile> profile = instagram.fetch(handle, RECENT_POSTS);
            if (profile.isPresent()) {
                applyInstagram(creator, profile.get());
                source = SOURCE_INSTAGRAM_PUBLIC;
            } else {
                unanswered.add("Instagram returned nothing for @" + handle
                        + " (only public Business and Creator accounts are visible)");
            }
        }

        String channel = youtubeChannelOf(creator, platform);
        if (channel != null && youtube.isEnabled()) {
            Optional<JsonNode> found = youtube.channel(channel);
            if (found.isPresent()) {
                // Subscribers only replace the follower count when YouTube is the primary platform;
                // otherwise they would overwrite the Instagram figure the rest of the app compares.
                applyYouTube(creator, found.get(), "YOUTUBE".equals(platform));
                if ("YOUTUBE".equals(platform)) {
                    youtubeEngagementRate(found.get()).ifPresent(creator::setErPercentage);
                }
                if (source == null) {
                    source = SOURCE_YOUTUBE_PUBLIC;
                }
            } else {
                unanswered.add("YouTube found no channel " + channel);
            }
        }

        if (applyConnectedDemographics(creator)) {
            source = SOURCE_INSTAGRAM_CONNECTED;
        }

        if (source == null) {
            String reason = unanswered.isEmpty()
                    ? "No free source covers this creator (TikTok has no public API, and no "
                            + "Instagram or YouTube handle is configured for lookup)."
                    : String.join("; ", unanswered) + ".";
            return EnrichmentResult.skipped(creator, reason);
        }

        creator.setInsightsSource(source);
        creator.setInsightsRefreshedAt(Instant.now());
        creatorRepository.save(creator);
        captureSnapshot(creator);

        log.info("Refreshed creator {} (@{}) from {}", creator.getId(), creator.getHandle(), source);
        return new EnrichmentResult(creator.getId(), creator.getHandle(), true,
                "Public profile refreshed.", creator.getInsightsRefreshedAt());
    }

    private void applyInstagram(Creator creator, InstagramProfile profile) {
        if (profile.followers() > 0) {
            setFollowers(creator, (int) Math.min(Integer.MAX_VALUE, profile.followers()));
            instagramEngagementRate(profile.posts(), profile.followers())
                    .ifPresent(creator::setErPercentage);
        }
        fillBioIfBlank(creator, profile.biography());
    }

    /**
     * Engagement rate on Instagram, calculated by us: (likes + comments) / followers, averaged over
     * the recent posts. Posts whose owner hid the like count are left out rather than counted as
     * zero, which would drag the average down for a reason that has nothing to do with engagement.
     */
    static Optional<BigDecimal> instagramEngagementRate(List<InstagramProfile.Post> posts, long followers) {
        if (followers <= 0) {
            return Optional.empty();
        }
        List<Double> rates = new ArrayList<>();
        for (InstagramProfile.Post post : posts) {
            if (post.likes() == null) {
                continue;
            }
            rates.add((post.likes() + post.comments()) * 100.0 / followers);
            if (rates.size() >= RECENT_POSTS) {
                break;
            }
        }
        return average(rates);
    }

    /**
     * Engagement rate on YouTube: (likes + comments) / views per video, averaged. YouTube publishes
     * views, and a video's audience is its viewers rather than the channel's subscribers.
     */
    private Optional<BigDecimal> youtubeEngagementRate(JsonNode channel) {
        String uploads = channel.path("contentDetails").path("relatedPlaylists").path("uploads").asText(null);
        List<String> videoIds = new ArrayList<>();
        youtube.recentUploads(uploads, RECENT_POSTS).ifPresent(body -> {
            for (JsonNode item : body.path("items")) {
                String id = item.path("contentDetails").path("videoId").asText(null);
                if (id != null) {
                    videoIds.add(id);
                }
            }
        });
        if (videoIds.isEmpty()) {
            return Optional.empty();
        }
        List<Double> rates = new ArrayList<>();
        youtube.videoStatistics(videoIds).ifPresent(body -> {
            for (JsonNode video : body.path("items")) {
                JsonNode stats = video.path("statistics");
                long views = stats.path("viewCount").asLong(0);
                if (views > 0) {
                    long engagements = stats.path("likeCount").asLong(0) + stats.path("commentCount").asLong(0);
                    rates.add(engagements * 100.0 / views);
                }
            }
        });
        return average(rates);
    }

    private static Optional<BigDecimal> average(List<Double> rates) {
        if (rates.isEmpty()) {
            return Optional.empty();
        }
        double mean = rates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Optional.of(BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP));
    }

    private void applyYouTube(Creator creator, JsonNode channel, boolean primary) {
        JsonNode statistics = channel.path("statistics");
        // A channel can hide its subscriber count; then the figure is absent, not zero.
        if (primary && !statistics.path("hiddenSubscriberCount").asBoolean(false)) {
            int subscribers = statistics.path("subscriberCount").asInt(0);
            if (subscribers > 0) {
                setFollowers(creator, subscribers);
            }
        }
        fillBioIfBlank(creator, channel.path("snippet").path("description").asText(null));
    }

    /**
     * Demographics for a creator who connected their Instagram. Only the platform provider can
     * answer; the mock's figures are invented and must never be written onto a real creator.
     */
    private boolean applyConnectedDemographics(Creator creator) {
        if (!(insightsProvider instanceof PlatformApiCreatorInsightsProvider)
                || !tokenCipher.isConfigured()) {
            return false;
        }
        Map<String, Object> audience = insightsProvider.getAudienceDemographics(creator.getId());
        if (audience == null || audience.isEmpty()) {
            return false;
        }
        decimal(audience.get("ukAudiencePct")).ifPresent(creator::setUkAudiencePct);
        string(audience.get("topAgeBand")).ifPresent(creator::setAudienceAgeBand);
        string(audience.get("genderSplit")).ifPresent(creator::setAudienceGenderSplit);
        if (audience.get("followers") instanceof Number followers && followers.intValue() > 0) {
            setFollowers(creator, followers.intValue());
        }
        return true;
    }

    private void setFollowers(Creator creator, int followers) {
        creator.setFollowersCount(followers);
        // The stored band was derived from the old count; let it re-derive.
        creator.setFollowerBand(null);
    }

    private void fillBioIfBlank(Creator creator, String bio) {
        if (isBlank(creator.getBio()) && !isBlank(bio)) {
            String trimmed = bio.trim();
            creator.setBio(trimmed.length() <= MAX_BIO_LENGTH ? trimmed : trimmed.substring(0, MAX_BIO_LENGTH));
        }
    }

    /** Requirement #49: follower growth needs a point in time per refresh, not one mutable number. */
    private void captureSnapshot(Creator creator) {
        LocalDate today = LocalDate.now();
        if (creator.getFollowersCount() != null
                && !snapshotRepository.existsByCreatorIdAndCapturedOn(creator.getId(), today)) {
            snapshotRepository.save(CreatorFollowerSnapshot.of(
                    creator.getId(), creator.getFollowersCount(), creator.getErPercentage()));
        }
    }

    // =====================================================================
    // Many creators
    // =====================================================================

    /** The creators whose public figures are oldest, up to {@code limit}. */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public List<EnrichmentResult> enrichStalest(int limit, boolean force) {
        requireSources();
        int batch = Math.clamp(limit, 1, maxBatch);
        List<Creator> candidates = creatorRepository.findStalestForEnrichment(
                force ? Instant.now() : staleBefore(), PageRequest.of(0, batch));
        return enrichAll(candidates, false);
    }

    /**
     * The daily job: creators on ACTIVE campaigns first, bounded by the batch size so one run
     * stays inside Meta's hourly Business Discovery allowance. No security context — it is called
     * by the scheduler, not a user.
     */
    @Transactional
    public List<EnrichmentResult> refreshActiveCampaignCreators() {
        if (!sourceStatus().live()) {
            return List.of();
        }
        return enrichAll(creatorRepository.findStaleOnActiveCampaigns(staleBefore(), maxBatch), false);
    }

    private List<EnrichmentResult> enrichAll(List<Creator> candidates, boolean force) {
        List<EnrichmentResult> results = new ArrayList<>();
        for (Creator creator : candidates) {
            try {
                results.add(enrichOne(creator, force));
            } catch (RuntimeException e) {
                // One bad profile must not abandon the rest of the batch.
                log.warn("Refreshing creator {} failed: {}", creator.getId(), e.getClass().getSimpleName());
                results.add(EnrichmentResult.skipped(creator, "The lookup failed; try again later."));
            }
        }
        log.info("Profile refresh: {} of {} creator(s) refreshed",
                results.stream().filter(EnrichmentResult::refreshed).count(), results.size());
        return results;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void requireSources() {
        if (!sourceStatus().live()) {
            throw ApiException.unprocessable(
                    "Profile refresh needs Instagram or YouTube configured on the server: set "
                            + "META_ACCESS_TOKEN and META_IG_USER_ID, or YOUTUBE_API_KEY.");
        }
    }

    private boolean isFresh(Creator creator) {
        return creator.getInsightsRefreshedAt() != null
                && creator.getInsightsRefreshedAt().isAfter(staleBefore());
    }

    private Instant staleBefore() {
        return Instant.now().minus(Duration.ofHours(ttlHours));
    }

    private static String platformOf(Creator creator) {
        String platform = creator.getPrimaryPlatform();
        return platform == null || platform.isBlank() ? "INSTAGRAM" : platform.trim().toUpperCase();
    }

    private static String youtubeChannelOf(Creator creator, String platform) {
        String channel = cleanHandle(creator.getYoutubeHandle());
        if (channel == null && "YOUTUBE".equals(platform)) {
            channel = cleanHandle(creator.getHandle());
        }
        if (channel == null) {
            return null;
        }
        // A channel id (UC…) is used as-is; anything else is a handle, which YouTube writes with @.
        return channel.startsWith("UC") && channel.length() == 24 ? channel : "@" + channel;
    }

    private static Optional<BigDecimal> decimal(Object value) {
        if (value instanceof Number number) {
            return Optional.of(BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }

    private static Optional<String> string(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static String cleanHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            return null;
        }
        String cleaned = handle.trim().replaceFirst("^@", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
