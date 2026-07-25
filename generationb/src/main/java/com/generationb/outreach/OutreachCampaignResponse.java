package com.generationb.outreach;

import java.time.Instant;
import java.util.UUID;

public record OutreachCampaignResponse(
    UUID id,
    UUID brandId,
    UUID campaignId,
    UUID templateId,
    String subject,
    String body,
    OutreachCampaignStatus status,
    Instant scheduledAt,
    Instant sentAt,
    int recipientCount
) {}
