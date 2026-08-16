package com.generationb.campaigns;

import java.time.Instant;

/** Q-E24: the campaign lifecycle is create, update, archive, unarchive, delete. */
public record UpdateCampaignCommand(
    String name,
    CampaignType campaignType,
    CampaignStatus status,
    Instant startDate,
    Instant endDate
) {}
