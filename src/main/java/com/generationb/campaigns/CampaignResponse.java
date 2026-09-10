package com.generationb.campaigns;

import java.time.Instant;
import java.util.UUID;

public record CampaignResponse(
    UUID id,
    UUID brandId,
    String name,
    CampaignType campaignType,
    CampaignStatus status,
    Instant startDate,
    Instant endDate,
    UUID createdBy,
    /**
     * The hashtag briefed creators are asked to post with. Goes in the brief; coverage matches it
     * against their own posts, which is what makes attribution exact and free.
     */
    String trackingHashtag,
    Instant createdAt,
    Instant updatedAt
) {}
