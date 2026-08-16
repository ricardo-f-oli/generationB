package com.generationb.creators;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// TODO(confirm): contrato Modash pendente de assinatura — ajustar mapeamento quando o contrato for assinado
public interface CreatorInsightsProvider {

    List<Map<String, Object>> searchCreators(String criteriaQuery, String platform, String niche);

    List<Map<String, Object>> getRecentActivity(UUID creatorId);

    Map<String, Object> getAudienceDemographics(UUID creatorId);

    List<Map<String, Object>> getMentions(String brandOrHashtag, int limit);
}
