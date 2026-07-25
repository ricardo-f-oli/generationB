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
    Instant createdAt,
    Instant updatedAt
) {}
