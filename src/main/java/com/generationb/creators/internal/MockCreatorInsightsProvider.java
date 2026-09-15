package com.generationb.creators.internal;

import com.generationb.creators.CreatorInsightsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Generated sample data, used whenever no Instagram or YouTube credential is configured, so a
 * fresh clone gets a working demo rather than an empty screen.
 *
 * <p>Also selected when {@code insights.provider=mock} is set explicitly, which is how you hold
 * real credentials without using them yet.
 *
 * <p>Every response is logged as {@code [MOCK INSIGHTS]} so nobody mistakes generated numbers for
 * a client's real coverage. {@link PlatformApiCreatorInsightsProvider} is the live one.
 */
@Slf4j
@Component
@Conditional(InsightsProviderCondition.Mock.class)
public class MockCreatorInsightsProvider implements CreatorInsightsProvider {

    @Override
    public List<Map<String, Object>> searchCreators(String criteriaQuery, String platform, String niche) {
        log.info("[MOCK INSIGHTS] Searching creators criteriaQuery: {}, platform: {}, niche: {}", criteriaQuery, platform, niche);
        return List.of(
                Map.of(
                        "handle", "sophiabeauty",
                        "name", "Sophia Styles",
                        "platform", platform != null ? platform : "INSTAGRAM",
                        "followers", 45000,
                        "er", 4.2,
                        "location", "London, UK",
                        "niche", niche != null ? niche : "Beauty"
                ),
                Map.of(
                        "handle", "marcuslifts",
                        "name", "Marcus Fitness",
                        "platform", platform != null ? platform : "INSTAGRAM",
                        "followers", 120000,
                        "er", 3.8,
                        "location", "Manchester, UK",
                        "niche", "Fitness"
                )
        );
    }

    @Override
    public List<Map<String, Object>> getRecentActivity(UUID creatorId) {
        log.info("[MOCK INSIGHTS] Fetching recent activity for creatorId: {}", creatorId);
        return List.of(
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "platform", "INSTAGRAM",
                        "postType", "REEL",
                        "url", "https://instagram.com/p/reel_mock_1",
                        "caption", "Loving this new Mediheal hydrating mask #ad #mediheal",
                        "views", 15400,
                        "likes", 1240,
                        "comments", 88,
                        "postedAt", Instant.now().minusSeconds(86400).toString()
                ),
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "platform", "TIKTOK",
                        "postType", "TIKTOK",
                        "url", "https://tiktok.com/@user/video/mock_2",
                        "caption", "Unboxing my Katie Loxton summer bag! #katieloxton #gifted",
                        "views", 28900,
                        "likes", 3100,
                        "comments", 142,
                        "postedAt", Instant.now().minusSeconds(172800).toString()
                )
        );
    }

    @Override
    public Map<String, Object> getAudienceDemographics(UUID creatorId) {
        log.info("[MOCK INSIGHTS] Fetching audience demographics for creatorId: {}", creatorId);
        return Map.of(
                "topLocation", "United Kingdom (68%)",
                "topAgeBand", "18-24 (45%), 25-34 (38%)",
                "genderSplit", "Female 78%, Male 22%",
                "authenticityScore", "92%"
        );
    }

    @Override
    public List<Map<String, Object>> getMentions(String brandOrHashtag, int limit) {
        log.info("[MOCK INSIGHTS] Searching mentions for: {}", brandOrHashtag);
        return List.of(
                Map.of(
                        "handle", "ellafashion",
                        "platform", "INSTAGRAM",
                        "postType", "STORY",
                        "url", "https://instagram.com/stories/ellafashion/1",
                        "mention", brandOrHashtag,
                        "views", 8500,
                        "postedAt", Instant.now().minusSeconds(43200).toString()
                )
        );
    }
}
