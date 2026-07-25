package com.generationb.shared;

import java.time.Instant;
import java.util.UUID;

public record CardMovedEvent(
    UUID cardId,
    UUID fromColumnId,
    UUID toColumnId,
    UUID brandId,
    Instant occurredAt
) {}
