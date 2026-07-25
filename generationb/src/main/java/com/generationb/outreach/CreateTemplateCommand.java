package com.generationb.outreach;

import com.generationb.outreach.OutreachType;

import java.util.UUID;

public record CreateTemplateCommand(
    String name,
    OutreachType type,
    UUID brandId,
    String subjectTemplate,
    String bodyTemplate
) {}
