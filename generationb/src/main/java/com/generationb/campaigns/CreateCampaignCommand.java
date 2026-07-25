package com.generationb.campaigns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateCampaignCommand(
    @NotBlank(message = "Name is required")
    String name,
    @NotNull(message = "Campaign type is required")
    CampaignType campaignType,
    Instant startDate,
    Instant endDate
) {}
