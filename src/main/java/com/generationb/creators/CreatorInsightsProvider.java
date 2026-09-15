package com.generationb.creators;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where creator posts, mentions and audience figures come from.
 *
 * <p>Two implementations: the free platform APIs (Instagram Business Discovery, Hashtag Search and
 * Insights for connected accounts; YouTube Data API) and a mock for development. See
 * {@code InsightsProviderCondition} for which one is registered.
 */
public interface CreatorInsightsProvider {

    List<Map<String, Object>> searchCreators(String criteriaQuery, String platform, String niche);

    List<Map<String, Object>> getRecentActivity(UUID creatorId);

    Map<String, Object> getAudienceDemographics(UUID creatorId);

    List<Map<String, Object>> getMentions(String brandOrHashtag, int limit);
}
