package com.generationb.creators;

import java.time.Instant;
import java.util.UUID;

public record CreatorNoteResponse(
    UUID id,
    UUID creatorId,
    UUID authorId,
    String authorName,
    String noteText,
    boolean confidential,
    int revisionCount,
    Instant createdAt,
    Instant updatedAt
) {}
