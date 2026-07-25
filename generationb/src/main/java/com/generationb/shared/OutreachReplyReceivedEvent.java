package com.generationb.shared;

import java.time.Instant;
import java.util.UUID;

public record OutreachReplyReceivedEvent(
    UUID recipientId,
    UUID creatorId,
    UUID brandId,
    UUID campaignId,
    Instant occurredAt
) {}
