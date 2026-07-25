package com.generationb.shared;

import java.time.Instant;
import java.util.UUID;

public record OutreachBatchSentEvent(
    UUID campaignId,
    UUID brandId,
    int recipientCount,
    Instant occurredAt
) {}
