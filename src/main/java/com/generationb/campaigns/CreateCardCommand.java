package com.generationb.campaigns;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateCardCommand(
    @NotNull(message = "Column ID is required")
    UUID columnId,
    @NotNull(message = "Creator ID is required")
    UUID creatorId,
    @NotNull(message = "Campaign ID is required")
    UUID campaignId,
    List<String> deliverables,
    BigDecimal feeAmount,
    String feeCurrency,
    LocalDate deadline,
    String notes
) {}
