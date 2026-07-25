package com.generationb.campaigns;

import java.util.List;
import java.util.UUID;

public record BoardWithCardsResponse(
    UUID id,
    UUID campaignId,
    UUID brandId,
    String name,
    List<ColumnWithCardsResponse> columns
) {
    public record ColumnWithCardsResponse(
        UUID id,
        String name,
        int displayOrder,
        boolean requiresDirectorApproval,
        boolean requiresClientApproval,
        boolean triggersEmail,
        UUID triggerTemplateId,
        List<CampaignCardResponse> cards
    ) {}
}
