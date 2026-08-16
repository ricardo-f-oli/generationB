package com.generationb.creators;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Q-E8: controllers used to return the JPA entity directly, which leaked internal fields and
 * blew up on lazy collections with open-in-view disabled.
 */
public record CreatorResponse(
    UUID id,
    String name,
    String handle,
    String email,
    String phone,
    String primaryPlatform,
    List<String> platforms,
    String tiktokHandle,
    String youtubeHandle,
    Integer followersCount,
    String followersDisplay,
    String followerBand,
    BigDecimal erPercentage,
    String location,
    String niche,
    String bio,
    String portfolioUrl,
    BigDecimal ukAudiencePct,
    String audienceAgeBand,
    String audienceGenderSplit,
    String qualityBand,
    String optInStatus,
    List<String> tags,
    List<BrandEngagement> brandEngagements,
    boolean workedWithOtherBrand,
    boolean suppressed,
    String lastContact,
    Instant createdAt
) {
    public record BrandEngagement(UUID brandId, String brandName, String status, Instant lastEngagedAt) {}
}
