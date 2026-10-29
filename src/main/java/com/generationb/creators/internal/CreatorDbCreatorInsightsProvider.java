package com.generationb.creators.internal;

import com.generationb.creators.CreatorInsightsProvider;
import com.generationb.foundation.insights.CreatorDbClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creator data from CreatorDB, the paid vendor, with the free platform APIs behind it.
 *
 * <p>Chosen on 2026-10-23 so that one source answers the whole workflow — discovery, profiles,
 * posts and audience demographics — on Instagram, TikTok and YouTube alike, which the platform
 * APIs cannot: they have no creator index, no TikTok route and no demographics for a creator who
 * has not connected.
 *
 * <h2>What comes from where</h2>
 *
 * <table>
 *   <tr><th>Question</th><th>CreatorDB</th><th>When it has nothing</th></tr>
 *   <tr><td>A creator's posts (#10)</td><td>content-detail, 3 credits a platform</td>
 *       <td>the platform APIs</td></tr>
 *   <tr><td>Audience demographics (#26)</td><td>audience, 10 credits</td>
 *       <td>the creator's connected Instagram</td></tr>
 *   <tr><td>Creator search (#23)</td><td>search, 1 credit</td><td>nothing</td></tr>
 *   <tr><td>Hashtag and mention search (#11, #25)</td><td>not offered</td>
 *       <td>Instagram Hashtag Search, always</td></tr>
 * </table>
 *
 * <p>The fallback is the platform provider when Meta or YouTube is configured, and nothing
 * otherwise — never the mock, whose generated figures must not mix with real ones.
 */
@Slf4j
@Primary
@Component
@Conditional(InsightsProviderCondition.CreatorDb.class)
public class CreatorDbCreatorInsightsProvider implements CreatorInsightsProvider {

    private static final int MAX_POSTS_PER_PLATFORM = 30;

    private final CreatorDbLookup creatorDb;
    private final CreatorDbClient client;
    private final CreatorRepository creatorRepository;
    private final ObjectProvider<PlatformApiCreatorInsightsProvider> platform;

    public CreatorDbCreatorInsightsProvider(CreatorDbLookup creatorDb, CreatorDbClient client,
                                            CreatorRepository creatorRepository,
                                            ObjectProvider<PlatformApiCreatorInsightsProvider> platform) {
        this.creatorDb = creatorDb;
        this.client = client;
        this.creatorRepository = creatorRepository;
        this.platform = platform;
    }

    @PostConstruct
    void announce() {
        if (!client.isEnabled()) {
            log.warn("INSIGHTS_PROVIDER=creatordb but CREATORDB_API_KEY is not set: every "
                    + "CreatorDB lookup will return nothing");
        }
        log.info("Creator data is coming from CreatorDB. Fallback for what it cannot answer: {}",
                platform.getIfAvailable() == null ? "none (no Meta or YouTube credentials)" : "the platform APIs");
    }

    @Override
    public List<Map<String, Object>> getRecentActivity(UUID creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId).orElse(null);
        if (creator == null) {
            log.warn("No active creator {} to fetch activity for", creatorId);
            return List.of();
        }

        List<Map<String, Object>> posts = new ArrayList<>();
        for (CreatorDbLookup.Target target : CreatorDbLookup.targetsOf(creator)) {
            for (Map<String, Object> post : creatorDb.recentPosts(target, MAX_POSTS_PER_PLATFORM)) {
                Map<String, Object> withReach = new LinkedHashMap<>(post);
                // The stored count, which is free; a profile call per clip is not.
                if (creator.getFollowersCount() != null && creator.getFollowersCount() > 0) {
                    withReach.putIfAbsent("authorFollowers", creator.getFollowersCount().longValue());
                }
                posts.add(withReach);
            }
        }
        if (!posts.isEmpty()) {
            return posts;
        }

        PlatformApiCreatorInsightsProvider fallback = platform.getIfAvailable();
        return fallback == null ? List.of() : fallback.getRecentActivity(creatorId);
    }

    /** The primary platform's audience; then a connected Instagram, if CreatorDB has none. */
    @Override
    public Map<String, Object> getAudienceDemographics(UUID creatorId) {
        Creator creator = creatorRepository.findActiveById(creatorId).orElse(null);
        if (creator == null) {
            return Map.of();
        }
        List<CreatorDbLookup.Target> targets = CreatorDbLookup.targetsOf(creator);
        if (!targets.isEmpty()) {
            Map<String, Object> audience = creatorDb.audience(targets.getFirst());
            if (!audience.isEmpty()) {
                return audience;
            }
        }
        PlatformApiCreatorInsightsProvider fallback = platform.getIfAvailable();
        return fallback == null ? Map.of() : fallback.getAudienceDemographics(creatorId);
    }

    @Override
    public List<Map<String, Object>> searchCreators(String criteriaQuery, String platformName, String niche) {
        return creatorDb.search(criteriaQuery, platformName, niche);
    }

    /** CreatorDB does not search posts by hashtag, so this stays on Instagram's Hashtag Search. */
    @Override
    public List<Map<String, Object>> getMentions(String brandOrHashtag, int limit) {
        PlatformApiCreatorInsightsProvider fallback = platform.getIfAvailable();
        return fallback == null ? List.of() : fallback.getMentions(brandOrHashtag, limit);
    }
}
