package com.generationb.outreach;

public record GenerateAiTemplateCommand(
    OutreachType type,
    String brandName,
    String campaignContext,
    String tone
) {}
