package com.generationb.campaigns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CampaignCardResponse(
    UUID id,
    UUID boardId,
    UUID columnId,
    UUID brandId,
    UUID creatorId,
    UUID campaignId,
    List<String> deliverables,
    BigDecimal feeAmount,
    String feeCurrency,
    LocalDate deadline,
    PaymentStatus paymentStatus,
    List<String> contentDraftUrls,
    ApprovalStatus approvalStatus,
    String notes
) {}
