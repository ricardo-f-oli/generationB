package com.generationb.outreach;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
    UUID id,
    String name,
    OutreachType type,
    UUID brandId,
    String subjectTemplate,
    String bodyTemplate,
    boolean aiGenerated,
    boolean isActive,
    Instant createdAt
) {}
