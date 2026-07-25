package com.generationb.outreach;

import java.time.Instant;
import java.util.UUID;

public record RecipientStatusResponse(
    UUID id,
    UUID creatorId,
    String creatorHandle,
    String creatorFirstName,
    RecipientStatus status,
    Instant sentAt,
    Instant openedAt,
    Instant repliedAt
) {}
