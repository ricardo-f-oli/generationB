package com.generationb.campaigns;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CampaignCardResponse(
    UUID id,
    UUID boardId,
    UUID columnId,
    UUID brandId,
    UUID creatorId,
    /** Resolved from the creators module so the board can label a card. */
    String creatorHandle,
    UUID campaignId,
    /** Q-E20: the frontend needs this to render and reorder cards deterministically. */
    int position,
    UUID briefId,
    UUID assigneeId,
    boolean blocked,
    List<String> deliverables,
    BigDecimal feeAmount,
    String feeCurrency,
    LocalDate deadline,
    PaymentStatus paymentStatus,
    List<String> contentDraftUrls,
    ApprovalStatus approvalStatus,
    UUID approvedBy,
    Instant approvedAt,
    String notes
) {}
