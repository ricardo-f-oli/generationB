package com.generationb.briefs;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record UpdateBriefCommand(
    @NotBlank(message = "Campaign name is required")
    String campaignName,
    String campaignGoal,
    String keyMessages,
    List<String> deliverables,
    BigDecimal budgetMin,
    BigDecimal budgetMax,
    Instant timelineStart,
    Instant timelineEnd,
    ToneOfVoice toneOfVoice,
    String additionalNotes
) {}
