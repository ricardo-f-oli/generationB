package com.generationb.outreach;

import java.time.Instant;
import java.util.UUID;

public record EmailThreadResponse(
    UUID id,
    ThreadDirection direction,
    String fromAddress,
    String subject,
    String bodyText,
    Instant receivedAt
) {}
