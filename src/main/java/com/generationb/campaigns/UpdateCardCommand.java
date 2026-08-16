package com.generationb.campaigns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** All fields optional — the mapper ignores nulls so a partial PATCH cannot wipe data (Q-E10). */
public record UpdateCardCommand(
    UUID columnId,
    List<String> deliverables,
    BigDecimal feeAmount,
    String feeCurrency,
    LocalDate deadline,
    List<String> contentDraftUrls,
    ApprovalStatus approvalStatus,
    String notes,
    UUID briefId,
    UUID assigneeId,
    Boolean blocked
) {}
