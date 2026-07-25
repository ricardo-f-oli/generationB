package com.generationb.shared;

import java.time.Instant;
import java.util.UUID;

public record FollowUpSuggestedEvent(
    UUID recipientId,
    UUID creatorId,
    UUID brandId,
    Instant occurredAt
) {}
