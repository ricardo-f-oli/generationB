package com.generationb.outreach;

import java.util.UUID;

public record CreateOutreachDraftCommand(
    UUID templateId,
    UUID campaignId,
    OutreachType outreachType,
    String subject,
    String body,
    String productName,
    int noReplyWindowDays
) {}
