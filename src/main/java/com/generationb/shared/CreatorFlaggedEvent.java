package com.generationb.shared;

import java.time.Instant;
import java.util.UUID;

public record CreatorFlaggedEvent(
    UUID creatorId,
    UUID brandId,
    String reason,
    Instant occurredAt
) {}
