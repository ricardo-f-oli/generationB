package com.generationb.outreach;

public record UpdateTemplateCommand(
    String name,
    String subjectTemplate,
    String bodyTemplate
) {}
