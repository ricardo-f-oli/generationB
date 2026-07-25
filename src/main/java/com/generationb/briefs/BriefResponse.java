package com.generationb.briefs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BriefResponse(
    UUID id,
    UUID brandId,
    String campaignName,
    String campaignGoal,
    String keyMessages,
    List<String> deliverables,
    BigDecimal budgetMin,
    BigDecimal budgetMax,
    Instant timelineStart,
    Instant timelineEnd,
    ToneOfVoice toneOfVoice,
    String additionalNotes,
    String aiGeneratedContent,
    BriefStatus status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
