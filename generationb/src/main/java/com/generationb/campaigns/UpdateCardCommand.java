package com.generationb.campaigns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdateCardCommand(
    UUID columnId,
    List<String> deliverables,
    BigDecimal feeAmount,
    String feeCurrency,
    LocalDate deadline,
    List<String> contentDraftUrls,
    ApprovalStatus approvalStatus,
    String notes
) {}
