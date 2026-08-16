package com.generationb.campaigns;

import java.time.Instant;
import java.util.UUID;

public record CardCommentResponse(
    UUID id,
    UUID cardId,
    UUID authorId,
    String authorName,
    String body,
    Instant createdAt
) {}
